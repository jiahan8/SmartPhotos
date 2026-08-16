/**
 * Import function triggers from their respective submodules:
 *
 * const {onCall} = require("firebase-functions/v2/https");
 * const {onDocumentWritten} = require("firebase-functions/v2/firestore");
 *
 * See a full list of supported triggers at https://firebase.google.com/docs/functions
 */

const {setGlobalOptions} = require("firebase-functions");
const {
  onDocumentCreated,
  onDocumentDeleted,
} = require("firebase-functions/v2/firestore");
const {onCall, HttpsError} = require("firebase-functions/v2/https");
const {defineSecret} = require("firebase-functions/params");
const logger = require("firebase-functions/logger");
const admin = require("firebase-admin");
const vision = require("@google-cloud/vision");

// Initialize Firebase Admin
admin.initializeApp();

// Initialize Vision API client (handles authentication automatically)
const visionClient = new vision.ImageAnnotatorClient();

// For cost control, set the maximum number of concurrent instances
setGlobalOptions({maxInstances: 10});

// Collection names shared by the functions below
const COLLECTION_USER = "user";
const COLLECTION_USERNAME = "username";
const COLLECTION_NOTE = "note";

// Unsplash access key, set via:
//   firebase functions:secrets:set UNSPLASH_ACCESS_KEY
// Kept server-side so it never ships inside the app (a bundled client
// secret can trivially be extracted from any APK).
const UNSPLASH_ACCESS_KEY = defineSecret("UNSPLASH_ACCESS_KEY");
const UNSPLASH_API_BASE_URL = "https://api.unsplash.com";

// Mirrors AppConstants.kt / ValidationUtils.kt on the client: client-side
// checks are UX-only, these are the actual enforcement boundary.
const MAX_USERNAME_LENGTH = 30;
const MAX_DISPLAY_NAME_LENGTH = 50;
const MAX_POST_TEXT_LENGTH = 500;
const USERNAME_PATTERN = /^[a-zA-Z0-9._]+$/;

// Anti-abuse cap on how many media items a single note may carry. Not
// mirrored client-side (the picker has no matching limit) since normal
// usage never approaches it; it only guards against a scripted/abusive
// caller forcing processTextRecognition to run the Vision API hundreds of
// times for one note.
const MAX_NOTE_MEDIA_ITEMS = 20;

// System/impersonation-prone words that may never be claimed as a username,
// checked case-insensitively.
const RESERVED_USERNAMES = new Set([
  "admin", "administrator", "root", "superuser", "moderator", "mod",
  "support", "help", "helpdesk", "contact", "info", "about",
  "security", "webmaster", "postmaster", "hostmaster",
  "system", "staff", "official", "team", "owner",
  "api", "www", "mail", "ftp", "firebase", "smartphotos", "smartcamera",
  "null", "undefined", "anonymous", "everyone", "here", "channel",
  "test", "sample", "guest",
  "login", "logout", "signup", "signin", "register", "password",
  "settings", "profile", "billing", "payment", "terms", "privacy",
]);

/**
 * Sends a data-only push notification to a user via FCM, using the token
 * stored on their user document. Data-only (no `notification` block) so the
 * client's FirebaseMessagingService.onMessageReceived always runs -- even
 * while the app is backgrounded/killed -- and can build a localized
 * notification with the correct deep-link intent; a `notification` block
 * would instead be auto-displayed by the OS in that state, bypassing our
 * code and losing the deep link. Clears the stored token if FCM reports it
 * as stale/unregistered, so a dead token doesn't linger and get retried
 * forever.
 * @param {string} uid The recipient's user document ID.
 * @param {Object<string, string>} data Key-value payload (e.g. noteId).
 */
async function sendPushToUser(uid, data = {}) {
  const userRef = admin.firestore().collection(COLLECTION_USER).doc(uid);
  const userSnap = await userRef.get();
  const fcmToken = userSnap.data() && userSnap.data().fcm_token;

  if (!fcmToken) {
    return;
  }

  try {
    await admin.messaging().send({
      token: fcmToken,
      data,
    });
  } catch (error) {
    const staleTokenCodes = [
      "messaging/registration-token-not-registered",
      "messaging/invalid-registration-token",
    ];
    if (staleTokenCodes.includes(error.code)) {
      await userRef.update({fcm_token: null});
    } else {
      throw error;
    }
  }
}

/**
 * Cloud Function to process text recognition, label detection, and
 * object detection on images in a note
 * This function is automatically triggered when a new note is created
 * It processes all images in the media_list and adds detection fields
 * Uses Google Cloud Vision API for text, label, and object detection
 */
exports.processTextRecognition = onDocumentCreated(
    {
      document: `${COLLECTION_USER}/{userId}/${COLLECTION_NOTE}/{noteId}`,
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
            .collection(COLLECTION_USER)
            .doc(userId)
            .collection(COLLECTION_NOTE)
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
                // Request text, label, and object detection in a single
                // Vision API call instead of three separate round trips.
                const [result] = await visionClient.annotateImage({
                  image: {source: {imageUri: imageUrl}},
                  features: [
                    {type: "TEXT_DETECTION"},
                    {type: "LABEL_DETECTION"},
                    {type: "OBJECT_LOCALIZATION"},
                  ],
                });

                // Process text detection - create array of detected text
                const generatedText = [];
                const textDetections = result.textAnnotations;
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
                const labels = result.labelAnnotations;
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
                const objects = result.localizedObjectAnnotations;
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

        // Only notify when the note actually carries a photo/video -- a
        // text-only note has no image to show as the notification icon and
        // isn't the kind of "processing finished" event worth interrupting
        // the user for.
        const firstMedia = updatedMediaList.find(
            (media) => media.photoUrl || media.videoUrl,
        );
        if (firstMedia) {
          const pushData = {noteId};
          const previewUrl = firstMedia.thumbnailUrl || firstMedia.photoUrl;
          if (previewUrl) {
            pushData.mediaUrl = previewUrl;
          }
          try {
            await sendPushToUser(userId, pushData);
          } catch (error) {
            logger.error("Error sending push notification:", error);
          }
        }

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

/**
 * Strips a client-supplied media item down to the fields a client may
 * legitimately set before upload/detection finishes. generatedText/
 * generatedObjects/generatedLabels are intentionally dropped here: those may
 * only be added later, server-side, by processTextRecognition -- otherwise a
 * caller could fabricate detection results to manipulate search.
 * @param {object} media A single client-supplied media item.
 * @return {object} The sanitized media item to persist.
 */
function sanitizeMediaItem(media) {
  const item = (media && typeof media === "object") ? media : {};
  return {
    photoUrl: typeof item.photoUrl === "string" ? item.photoUrl : null,
    videoUrl: typeof item.videoUrl === "string" ? item.videoUrl : null,
    thumbnailUrl:
      typeof item.thumbnailUrl === "string" ? item.thumbnailUrl : null,
    video: item.isVideo === true,
  };
}

/**
 * Callable that creates a note under user/{uid}/note. Replaces the client's
 * direct Firestore write so the server -- not the client -- enforces
 * MAX_POST_TEXT_LENGTH, stamps the true owner into user_id (getHomeNote() on
 * the client trusts that field to look up whose username/profile picture to
 * render alongside the note), and strips any generatedText/generatedObjects/
 * generatedLabels a caller tries to inject at creation time.
 */
exports.createNote = onCall(async (request) => {
  const uid = request.auth && request.auth.uid;
  if (!uid) {
    throw new HttpsError("unauthenticated", "Sign-in required.");
  }

  // Every invalid-argument error below carries a machine-readable `reason`
  // in its details -- noteErrorMessageResId() on the client maps these to
  // localized strings. All of them share the invalid-argument code, so the
  // client can't tell them apart from the code alone.
  const rawText = request.data && request.data.text;
  if (rawText !== null && rawText !== undefined &&
      typeof rawText !== "string") {
    throw new HttpsError(
        "invalid-argument", "Text must be a string.",
        {reason: "INVALID_TEXT_TYPE"},
    );
  }
  const trimmedText = typeof rawText === "string" ? rawText.trim() : null;
  const text = trimmedText ? trimmedText : null;
  if (text && text.length > MAX_POST_TEXT_LENGTH) {
    throw new HttpsError(
        "invalid-argument", "Text is too long.",
        {reason: "TEXT_TOO_LONG"},
    );
  }

  const rawMediaList = (request.data && request.data.mediaList) || [];
  if (!Array.isArray(rawMediaList)) {
    throw new HttpsError(
        "invalid-argument", "mediaList must be an array.",
        {reason: "INVALID_MEDIA_LIST_TYPE"},
    );
  }
  if (rawMediaList.length > MAX_NOTE_MEDIA_ITEMS) {
    throw new HttpsError(
        "invalid-argument", "Too many media items.",
        {reason: "TOO_MANY_MEDIA_ITEMS"},
    );
  }
  const mediaList = rawMediaList.map(sanitizeMediaItem);

  if (!text && mediaList.length === 0) {
    throw new HttpsError(
        "invalid-argument", "Note must have text or media.",
        {reason: "EMPTY_NOTE"},
    );
  }

  const noteRef = admin.firestore()
      .collection(COLLECTION_USER)
      .doc(uid)
      .collection(COLLECTION_NOTE)
      .doc();

  await noteRef.set({
    text: text,
    created: admin.firestore.FieldValue.serverTimestamp(),
    favorite: false,
    media_list: mediaList,
    user_id: uid,
  });

  return {documentPath: noteRef.id};
});

/**
 * Builds the Firestore document reference for a reserved username.
 * Usernames are reserved case-insensitively: the document ID is always
 * lowercased, while the original casing is kept in the `username` field.
 * @param {string} username The username to look up.
 * @return {admin.firestore.DocumentReference} The reservation doc reference.
 */
function usernameDocRef(username) {
  return admin.firestore()
      .collection(COLLECTION_USERNAME)
      .doc(username.toLowerCase());
}

/**
 * Throws an `invalid-argument` error if the given username is a reserved
 * system/impersonation-prone word (checked case-insensitively).
 * @param {string} username The username to validate.
 */
function assertUsernameNotReserved(username) {
  if (RESERVED_USERNAMES.has(username.toLowerCase())) {
    throw new HttpsError("invalid-argument", "This username is reserved.");
  }
}

/**
 * Throws an `invalid-argument` error if the given username violates the
 * length/character-set rules also enforced (for UX) in ValidationUtils.kt.
 * @param {string} username The username to validate.
 */
function assertValidUsernameFormat(username) {
  if (username.length > MAX_USERNAME_LENGTH) {
    throw new HttpsError("invalid-argument", "Username is too long.");
  }
  if (!USERNAME_PATTERN.test(username)) {
    throw new HttpsError(
        "invalid-argument",
        "Username contains invalid characters.",
    );
  }
}

/**
 * Callable that reports whether a username is available. Reads the
 * `username` reservation doc via the Admin SDK so clients never need direct
 * Firestore access to that collection (which stays fully locked down in
 * firestore.rules). Callable without auth, since this runs before sign-up.
 * Non-authoritative: createUserProfile/updateUsername still enforce
 * uniqueness atomically inside a transaction.
 */
exports.isUsernameAvailable = onCall(async (request) => {
  const username = request.data && request.data.username;
  if (typeof username !== "string" || username.trim().length === 0) {
    throw new HttpsError("invalid-argument", "Username is required.");
  }
  if (RESERVED_USERNAMES.has(username.toLowerCase())) {
    return {available: false};
  }
  const usernameSnapshot = await usernameDocRef(username).get();
  return {available: !usernameSnapshot.exists};
});

/**
 * Callable that reports whether an email address is already registered.
 * Uses Firebase Auth (via the Admin SDK) as the source of truth rather
 * than Firestore, since a `user` document may not exist yet even
 * though the Auth account does (e.g. the app was killed right after
 * account creation, before createUserProfile ran). Callable without auth,
 * since this runs before sign-up.
 */
exports.isEmailRegistered = onCall(async (request) => {
  const email = request.data && request.data.email;
  if (typeof email !== "string" || email.trim().length === 0) {
    throw new HttpsError("invalid-argument", "Email is required.");
  }

  try {
    await admin.auth().getUserByEmail(email);
    return {registered: true};
  } catch (error) {
    if (error.code === "auth/user-not-found") {
      return {registered: false};
    }
    throw error;
  }
});

/**
 * Callable that atomically reserves a username and creates the caller's
 * `user/{uid}` profile document. Replaces the client's direct Firestore
 * writes so uniqueness can be enforced with a transaction instead of a
 * racy read-then-write from the client.
 */
exports.createUserProfile = onCall(async (request) => {
  const uid = request.auth && request.auth.uid;
  if (!uid) {
    throw new HttpsError("unauthenticated", "Sign-in required.");
  }

  const metadata = request.data && request.data.metadata;
  const username = request.data && request.data.username;
  if (typeof username !== "string" || username.trim().length === 0) {
    throw new HttpsError("invalid-argument", "Username is required.");
  }
  if (typeof metadata !== "string") {
    throw new HttpsError("invalid-argument", "Metadata is required.");
  }
  assertValidUsernameFormat(username);
  assertUsernameNotReserved(username);

  // Read canonical profile fields from Auth rather than trusting the
  // client, since only auth.uid is verified by the callable context.
  const authUser = await admin.auth().getUser(uid);
  if (authUser.displayName &&
      authUser.displayName.length > MAX_DISPLAY_NAME_LENGTH) {
    throw new HttpsError("invalid-argument", "Display name is too long.");
  }
  const firestore = admin.firestore();
  const userRef = firestore.collection(COLLECTION_USER).doc(uid);
  const usernameRef = usernameDocRef(username);

  await firestore.runTransaction(async (transaction) => {
    const usernameSnapshot = await transaction.get(usernameRef);
    if (usernameSnapshot.exists && usernameSnapshot.data().uid !== uid) {
      throw new HttpsError("already-exists", "Username is taken.");
    }

    transaction.set(usernameRef, {uid, username});
    transaction.set(userRef, {
      email: authUser.email || null,
      metadata: metadata,
      display_name: authUser.displayName || null,
      username: username,
      profile_picture: null,
      created: admin.firestore.FieldValue.serverTimestamp(),
      user_id: uid,
    });
  });

  return {success: true};
});

/**
 * Callable that atomically changes the caller's username: reserves the
 * new username, releases the old reservation, and updates `user/{uid}`.
 */
exports.updateUsername = onCall(async (request) => {
  const uid = request.auth && request.auth.uid;
  if (!uid) {
    throw new HttpsError("unauthenticated", "Sign-in required.");
  }

  const username = request.data && request.data.username;
  if (typeof username !== "string" || username.trim().length === 0) {
    throw new HttpsError("invalid-argument", "Username is required.");
  }
  assertValidUsernameFormat(username);
  assertUsernameNotReserved(username);

  const firestore = admin.firestore();
  const userRef = firestore.collection(COLLECTION_USER).doc(uid);
  const usernameRef = usernameDocRef(username);

  await firestore.runTransaction(async (transaction) => {
    const userSnapshot = await transaction.get(userRef);
    if (!userSnapshot.exists) {
      throw new HttpsError(
          "failed-precondition",
          "User profile does not exist.",
      );
    }

    const usernameSnapshot = await transaction.get(usernameRef);
    if (usernameSnapshot.exists && usernameSnapshot.data().uid !== uid) {
      throw new HttpsError("already-exists", "Username is taken.");
    }

    const oldUsername = userSnapshot.data().username;
    if (oldUsername && oldUsername.toLowerCase() !== username.toLowerCase()) {
      transaction.delete(usernameDocRef(oldUsername));
    }

    transaction.set(usernameRef, {uid, username});
    transaction.update(userRef, {username: username});
  });

  return {success: true};
});

const DOWNLOAD_URL_PATTERN = /\/v0\/b\/([^/]+)\/o\/([^?]+)/;

/**
 * Extracts the storage bucket and object path from a Firebase Storage
 * download URL.
 * @param {string} url A Firebase Storage download URL.
 * @return {{bucket: string, path: string}|null} The parsed bucket/path,
 *   or null if the URL doesn't match the expected shape.
 */
function parseStorageDownloadUrl(url) {
  if (typeof url !== "string") {
    return null;
  }
  const match = url.match(DOWNLOAD_URL_PATTERN);
  if (!match) {
    return null;
  }
  return {bucket: match[1], path: decodeURIComponent(match[2])};
}

/**
 * Deletes the Storage objects (photo/video/thumbnail) referenced by a
 * note's `media_list` when the note document is deleted, so removing a
 * note doesn't leak Storage files.
 */
exports.cleanupNoteMedia = onDocumentDeleted(
    `${COLLECTION_USER}/{userId}/${COLLECTION_NOTE}/{noteId}`,
    async (event) => {
      const snapshot = event.data;
      if (!snapshot) {
        return null;
      }

      const mediaList = snapshot.data().media_list || [];
      const urls = mediaList
          .reduce((acc, media) => acc.concat(
              [media.photoUrl, media.videoUrl, media.thumbnailUrl],
          ), [])
          .filter(Boolean);

      if (urls.length === 0) {
        return null;
      }

      const results = await Promise.allSettled(
          urls.map(async (url) => {
            const parsed = parseStorageDownloadUrl(url);
            if (!parsed) {
              logger.warn(`Could not parse storage URL: ${url}`);
              return;
            }
            await admin.storage()
                .bucket(parsed.bucket)
                .file(parsed.path)
                .delete({ignoreNotFound: true});
          }),
      );

      results.forEach((result, index) => {
        if (result.status === "rejected") {
          logger.error(
              `Failed to delete storage object for ${urls[index]}:`,
              result.reason,
          );
        }
      });

      return null;
    },
);

/**
 * Callable that proxies Unsplash's photo-listing endpoint so the Unsplash
 * access key stays server-side instead of being bundled into the app.
 */
exports.listUnsplashPhotos = onCall(
    {secrets: [UNSPLASH_ACCESS_KEY]},
    async (request) => {
      if (!(request.auth && request.auth.uid)) {
        throw new HttpsError("unauthenticated", "Sign-in required.");
      }

      const page = (request.data && request.data.page) || 1;
      const perPage = (request.data && request.data.perPage) || 10;
      const orderBy = (request.data && request.data.orderBy) || "latest";

      const url = new URL(`${UNSPLASH_API_BASE_URL}/photos`);
      url.searchParams.set("page", String(page));
      url.searchParams.set("per_page", String(perPage));
      url.searchParams.set("order_by", String(orderBy));

      const response = await fetch(url, {
        headers: {
          "Authorization": `Client-ID ${UNSPLASH_ACCESS_KEY.value()}`,
        },
      });

      if (!response.ok) {
        logger.error(`Unsplash request failed with status ${response.status}`);
        throw new HttpsError("unavailable", "Failed to fetch photos.");
      }

      return {photos: await response.json()};
    },
);
