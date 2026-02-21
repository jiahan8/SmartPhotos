/**
 * Import function triggers from their respective submodules:
 *
 * const {onCall} = require("firebase-functions/v2/https");
 * const {onDocumentWritten} = require("firebase-functions/v2/firestore");
 *
 * See a full list of supported triggers at https://firebase.google.com/docs/functions
 */

const {setGlobalOptions} = require("firebase-functions");
const {onDocumentCreated} = require("firebase-functions/v2/firestore");
const logger = require("firebase-functions/logger");
const admin = require("firebase-admin");
const vision = require("@google-cloud/vision");

// Initialize Firebase Admin
admin.initializeApp();

// Initialize Vision API client (handles authentication automatically)
const visionClient = new vision.ImageAnnotatorClient();

// For cost control, set the maximum number of concurrent instances
setGlobalOptions({maxInstances: 10});

/**
 * Cloud Function to process text recognition, label detection, and
 * object detection on images in a note
 * This function is automatically triggered when a new note is created
 * It processes all images in the media_list and adds detection fields
 * Uses Google Cloud Vision API for text, label, and object detection
 */
exports.processTextRecognition = onDocumentCreated(
    {
      document: "user/{userId}/note/{noteId}",
      maxInstances: 5,
    },
    async (event) => {
      try {
        const snapshot = event.data;
        if (!snapshot) {
          logger.warn("No data associated with the event");
          return null;
        }

        const noteData = snapshot.data();
        const userId = event.params.userId;
        const noteId = event.params.noteId;

        logger.info(`Processing text recognition for note: ${noteId}`, {
          userId,
          noteId,
        });

        const mediaList = noteData.media_list || [];

        // If there are no media items, skip processing
        if (mediaList.length === 0) {
          logger.info("No media items to process");
          return null;
        }

        // Get reference to the note document for updating
        const noteRef = admin.firestore()
            .collection("user")
            .doc(userId)
            .collection("note")
            .doc(noteId);

        // Process each media item that has a photoUrl or thumbnailUrl
        const updatedMediaList = await Promise.all(
            mediaList.map(async (media) => {
              // Skip if video without thumbnail or no image URL
              const imageUrl = media.photoUrl || media.thumbnailUrl;

              if (!imageUrl) {
                logger.info("Skipping media item - no image URL found");
                return media;
              }

              try {
                // Use Vision client to detect text, labels, and objects
                const [textResult] =
                  await visionClient.textDetection(imageUrl);
                const [labelResult] =
                  await visionClient.labelDetection(imageUrl);
                const [objectResult] =
                  await visionClient.objectLocalization(imageUrl);

                // Process text detection - create array of detected text
                const generatedText = [];
                const textDetections = textResult.textAnnotations;
                if (textDetections && textDetections.length > 0) {
                  // First annotation contains the entire detected text
                  const fullText = textDetections[0].description || "";
                  if (fullText) {
                    // Split by newlines and add each line as array item
                    const lines = fullText
                        .split("\n")
                        .filter((line) => line.trim());
                    generatedText.push(...lines);
                  }
                }

                // Process label detection
                // Create array of maps with label and score
                const generatedLabels = [];
                const labels = labelResult.labelAnnotations;
                if (labels && labels.length > 0) {
                  labels.forEach((label) => {
                    generatedLabels.push({
                      label: label.description,
                      score: label.score,
                    });
                  });
                }

                // Process object detection
                // Create array of maps with object and score
                const generatedObjects = [];
                const objects = objectResult.localizedObjectAnnotations;
                if (objects && objects.length > 0) {
                  objects.forEach((object) => {
                    generatedObjects.push({
                      object: object.name,
                      score: object.score,
                    });
                  });
                }

                logger.info(
                    `Detection results - Text: ${generatedText.length}, ` +
                    `Labels: ${generatedLabels.length}, ` +
                    `Objects: ${generatedObjects.length}`,
                );

                // Return updated media object with all detection fields
                return {
                  ...media,
                  generatedText: generatedText,
                  generatedLabels: generatedLabels,
                  generatedObjects: generatedObjects,
                };
              } catch (error) {
                logger.error("Error processing image:", error);
                // Return media with empty arrays if processing fails
                return {
                  ...media,
                  generatedText: [],
                  generatedLabels: [],
                  generatedObjects: [],
                };
              }
            }),
        );

        // Update the note document with the new media_list
        await noteRef.update({
          media_list: updatedMediaList,
        });

        const msg = `Successfully processed text recognition`;
        logger.info(`${msg} for note: ${noteId}`);
        logger.info(`Processed ${updatedMediaList.length} media items`);

        return null;
      } catch (error) {
        logger.error("Error in processTextRecognition:", error);
        // Don't throw - just log the error to prevent retries
        return null;
      }
    });
