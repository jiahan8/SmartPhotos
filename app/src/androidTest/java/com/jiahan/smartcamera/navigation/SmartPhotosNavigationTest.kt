package com.jiahan.smartcamera.navigation

import android.content.Intent
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.performClick
import androidx.core.net.toUri
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.DialogNavigator
import androidx.navigation.testing.TestNavHostController
import androidx.navigation.toRoute
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jiahan.smartcamera.HiltTestActivity
import com.jiahan.smartcamera.R
import com.jiahan.smartcamera.SmartPhotosApp
import com.jiahan.smartcamera.auth.AuthRoute
import com.jiahan.smartcamera.core.ui.R as UiR
import com.jiahan.smartcamera.data.di.DataModule
import com.jiahan.smartcamera.data.repository.AnalyticsRepository
import com.jiahan.smartcamera.data.repository.AppUpdateRepository
import com.jiahan.smartcamera.data.repository.AuthRepository
import com.jiahan.smartcamera.data.repository.MediaFileRepository
import com.jiahan.smartcamera.data.repository.MediaUploadRepository
import com.jiahan.smartcamera.data.repository.NoteRepository
import com.jiahan.smartcamera.data.repository.PhotoRepository
import com.jiahan.smartcamera.data.repository.RemoteConfigRepository
import com.jiahan.smartcamera.data.repository.UserRepository
import com.jiahan.smartcamera.data.datastore.UserPreferencesRepository
import com.jiahan.smartcamera.fake.FakeAnalyticsRepository
import com.jiahan.smartcamera.fake.FakeAuthRepository
import com.jiahan.smartcamera.fake.FakeMediaFileRepository
import com.jiahan.smartcamera.fake.FakeMediaUploadRepository
import com.jiahan.smartcamera.fake.FakeNoteRepository
import com.jiahan.smartcamera.fake.FakePhotoRepository
import com.jiahan.smartcamera.fake.FakeRemoteConfigRepository
import com.jiahan.smartcamera.fake.FakeUserPreferencesRepository
import com.jiahan.smartcamera.fake.FakeUserRepository
import com.jiahan.smartcamera.feature.profile.R as ProfileR
import com.jiahan.smartcamera.home.HomeRoute
import com.jiahan.smartcamera.note.NoteRoute
import com.jiahan.smartcamera.preview.NotePreviewRoute
import com.jiahan.smartcamera.search.SearchRoute
import com.jiahan.smartcamera.search.SearchRoute.SEARCH_DEEP_LINK_URI_PATTERN
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.reflect.KClass

private const val NAVIGATION_TIMEOUT_MS = 5_000L

/**
 * The nav graph `:app` exists to host, exercised end-to-end: the bottom bar, the tab wiring, and
 * the two effects that navigate on their own.
 *
 * **This is the only suite that renders `SmartPhotosApp`, and until it existed nothing rendered the
 * NavHost at all.** `TopLevelDestinationTest` (unit) pins the tab *metadata* -- that the routes are
 * `@Serializable`, distinct, and in the intended order -- but it cannot see whether tapping one
 * arrives anywhere, because [TopLevelDestination.route] is typed `Any` and a route Navigation
 * cannot match fails by doing nothing at all. That silence is what this suite converts into a
 * failure.
 *
 * **Why it needs Hilt when the nine feature screen suites do not.** Those construct one ViewModel
 * from fakes and pass it in. Here the subject *is* the graph, and every screen inside it defaults
 * its ViewModel to `hiltViewModel()`, so the whole data layer has to resolve. `DataModule` is
 * uninstalled and its nine bindings replaced with `:core:testing`'s fakes, which is what keeps this
 * offline -- with the real module installed, composing Home reaches Firebase. Uninstalling is
 * per-class, so `HiltGraphSmokeTest` still resolves the real production graph, which is its whole
 * point.
 *
 * **Assertions read the back stack, through a [TestNavHostController] passed in.** That is the
 * officially documented way to test Navigation Compose, and it is available here only because
 * `SmartPhotosApp` takes its controller as a parameter; it used to remember one internally, which
 * left the bottom bar's *selected* state as the sole observable. Selection is derived state --
 * `NavigationBarItem(selected = …)` is set from `currentDestination.hasRoute(...)` -- so it works
 * for the five top-level destinations and says nothing at all about the other seven. Every
 * assertion that had to be written as `assertDoesNotExist()` on a tab was really asking "did we
 * leave?", and would have passed just as happily for a blank screen or a NavHost that never
 * composed. The tab assertions are kept where they apply, because the bar is real UI a user reads,
 * but the route is now what the test actually pins.
 */
@RunWith(AndroidJUnit4::class)
@HiltAndroidTest
@UninstallModules(DataModule::class)
class SmartPhotosNavigationTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<HiltTestActivity>()

    private lateinit var navController: TestNavHostController

    /*
     * The nine bindings DataModule would have supplied. All but the last come from :core:testing;
     * FakeAppUpdateRepository is local, for the module reason its own file records.
     */
    @BindValue
    @JvmField
    val noteRepository: NoteRepository = FakeNoteRepository()

    @BindValue
    @JvmField
    val authRepository: AuthRepository = FakeAuthRepository()

    @BindValue
    @JvmField
    val userRepository: UserRepository = FakeUserRepository()

    @BindValue
    @JvmField
    val userPreferencesRepository: UserPreferencesRepository = FakeUserPreferencesRepository()

    @BindValue
    @JvmField
    val analyticsRepository: AnalyticsRepository = FakeAnalyticsRepository()

    @BindValue
    @JvmField
    val remoteConfigRepository: RemoteConfigRepository = FakeRemoteConfigRepository()

    @BindValue
    @JvmField
    val mediaFileRepository: MediaFileRepository = FakeMediaFileRepository()

    @BindValue
    @JvmField
    val mediaUploadRepository: MediaUploadRepository = FakeMediaUploadRepository()

    @BindValue
    @JvmField
    val photoRepository: PhotoRepository = FakePhotoRepository()

    @BindValue
    @JvmField
    val appUpdateRepository: AppUpdateRepository = FakeAppUpdateRepository()

    private var scrollToTopCount = 0
    private var consumedPendingNoteId = false

    @Before
    fun inject() {
        hiltRule.inject()
    }

    private fun launchApp(
        startDestination: Any = HomeRoute,
        isAppReady: Boolean = true,
        isBottomBarVisible: Boolean = true,
        hasPendingShare: Boolean = false,
        pendingNoteId: String? = null,
    ) {
        composeTestRule.setContent {
            /*
             * Both navigators, and the second one is the trap. `NavHost` looks up ComposeNavigator
             * *and* DialogNavigator on the provider and `return`s early if either is absent -- so a
             * controller missing one renders nothing at all, with no exception and no log line, and
             * every assertion below fails as though navigation were broken. TestNavHostController
             * installs a TestNavigatorProvider that has neither, which is why they are added here
             * rather than inherited. The graph declares only `composable<…>` destinations today;
             * DialogNavigator is required regardless of whether one is used.
             */
            val context = LocalContext.current
            navController = remember(context) {
                TestNavHostController(context).apply {
                    navigatorProvider.addNavigator(ComposeNavigator())
                    navigatorProvider.addNavigator(DialogNavigator())
                }
            }
            SmartPhotosApp(
                isDarkTheme = false,
                isAppReady = isAppReady,
                startDestination = startDestination,
                isBottomBarVisible = isBottomBarVisible,
                scrollToTopRequestedAt = null,
                hasPendingShare = hasPendingShare,
                pendingNoteId = pendingNoteId,
                isUpdateReadyToInstall = false,
                onScrollDirectionChanged = {},
                onScrollToTopConsumed = {},
                onTriggerScrollToTop = { scrollToTopCount++ },
                onUpdateStartDestination = {},
                onPendingNoteIdConsumed = { consumedPendingNoteId = true },
                onCompleteUpdate = {},
                navController = navController,
            )
        }
        composeTestRule.waitForIdle()
    }

    private fun string(resId: Int) = composeTestRule.activity.getString(resId)

    /**
     * Matches a bottom-bar tab by its label, and only a tab.
     *
     * A bare text match is ambiguous exactly where it matters: Profile renders the word twice on
     * its own screen -- once as the screen title, once as the tab -- so `onNodeWithText` finds two
     * nodes and throws. `NavigationBarItem` is selectable and carries `Role.Tab`; scoping on that
     * separates the bar from whatever the screen happens to say. Same problem, same shape of fix as
     * `SettingsScreenTest`'s `hasAnyAncestor(isDialog())`.
     */
    private fun tabMatcher(resId: Int) = hasText(string(resId)) and isSelectable()

    private fun tab(resId: Int) = composeTestRule.onNode(tabMatcher(resId))

    /**
     * Reads the destination the back stack is currently on.
     *
     * Through `runOnIdle` because a NavController is main-thread state and the test body is not the
     * main thread -- and because it drains the navigation the tap just started before reading, so
     * these are never a race against the enter transition.
     */
    private fun currentDestination() =
        composeTestRule.runOnIdle { navController.currentDestination }

    /**
     * Asserts the back stack's current destination is [routeClass]'s.
     *
     * Keyed on the class rather than an instance because a route carrying arguments
     * ([NotePreviewRoute]) would otherwise have to be constructed with throwaway values just to
     * reach `::class`. Arguments are asserted where they matter by reading the route back with
     * `toRoute`.
     */
    private fun assertOnRoute(routeClass: KClass<*>) {
        val destination = currentDestination()
        assertTrue(
            "expected to be on ${routeClass.simpleName}, was ${destination?.route}",
            destination?.hasRoute(routeClass) == true,
        )
    }

    /** Waits until the back stack reaches [routeClass]'s destination. */
    private fun waitUntilOnRoute(routeClass: KClass<*>) {
        composeTestRule.waitUntil(timeoutMillis = NAVIGATION_TIMEOUT_MS) {
            navController.currentDestination?.hasRoute(routeClass) == true
        }
    }

    @Test
    fun startsOnHome_withHomeTabSelected() {
        launchApp()

        assertOnRoute(HomeRoute::class)
        tab(R.string.home).assertIsSelected()
        tab(UiR.string.search).assertIsNotSelected()
    }

    /**
     * The tab wiring, one destination at a time -- that the tap lands on the route the enum names,
     * and that the bar reflects it.
     *
     * A route the graph does not register leaves the back stack where it was rather than throwing.
     * Asserting the destination is what catches that; asserting the tab as well is what catches a
     * bar wired to the right route but reading its selection from the wrong one.
     */
    @Test
    fun everyBottomBarTab_navigatesToItsDestination() {
        launchApp()

        // Home last, so the walk ends back where it started rather than on Profile.
        val visitOrder =
            TopLevelDestination.entries.filterNot { it == TopLevelDestination.HOME } +
                    TopLevelDestination.HOME

        visitOrder.forEach { destination ->
            tab(destination.titleResId).performClick()
            waitUntilOnRoute(destination.route::class)

            assertOnRoute(destination.route::class)
            tab(destination.titleResId).assertIsSelected()
            TopLevelDestination.entries
                .filterNot { it == destination }
                .forEach { other -> tab(other.titleResId).assertIsNotSelected() }
        }
    }

    /** Re-tapping a list tab asks for a scroll-to-top; re-tapping a form tab has nowhere to go. */
    @Test
    fun reTappingASelectedTab_triggersScrollToTop_onlyForListTabs() {
        launchApp()

        tab(R.string.home).performClick()
        composeTestRule.waitForIdle()
        assertEquals(1, scrollToTopCount)

        tab(R.string.note).performClick()
        waitUntilOnRoute(NoteRoute::class)
        tab(R.string.note).performClick()
        composeTestRule.waitForIdle()

        assertEquals(
            "Note is a form, so re-tapping it must not request a scroll",
            1,
            scrollToTopCount,
        )
        // Still on Note: the second tap must not have popped or re-navigated.
        assertOnRoute(NoteRoute::class)
    }

    /**
     * The bar is shown only on the five top-level destinations. Auth is the case that matters --
     * it is a start destination in production, and a bottom bar there would let a signed-out user
     * walk into the app.
     */
    @Test
    fun bottomBar_isHiddenOnANonTopLevelDestination() {
        launchApp(startDestination = AuthRoute)

        assertOnRoute(AuthRoute::class)
        tab(R.string.home).assertDoesNotExist()
        tab(ProfileR.string.profile).assertDoesNotExist()
    }

    /** The scroll-away bar: hoisted state hides it even on a top-level destination. */
    @Test
    fun bottomBar_isHiddenWhenTheHostHidesIt() {
        launchApp(isBottomBarVisible = false)

        // On Home, where the bar would otherwise be -- the point is that it is hidden here, not
        // that we navigated somewhere it never shows.
        assertOnRoute(HomeRoute::class)
        tab(R.string.home).assertDoesNotExist()
    }

    /**
     * Nothing renders before the app reports itself ready -- the splash still owns the screen.
     *
     * The NavHost is what sets the graph, so an unready app leaves the back stack empty. That is
     * the assertion the tab check could not make: an absent Home tab is also what a graph pointed
     * at the wrong start destination looks like.
     */
    @Test
    fun navHost_doesNotRenderUntilTheAppIsReady() {
        launchApp(isAppReady = false)

        assertNull(currentDestination())
        tab(R.string.home).assertDoesNotExist()
    }

    /**
     * The search deep link, delivered the way the system delivers it.
     *
     * `handleDeepLink(intent)` rather than `navigate(uri)` because it is the production path: a
     * `NavController` calls it with the hosting activity's intent when the graph is created, which
     * is the *only* thing that acts on `live://…/search` -- `MainActivity` never reads
     * `intent.data`, so nothing else in `:app` would notice if this stopped matching. An implicit
     * `ACTION_VIEW` intent carries no `KEY_DEEP_LINK_IDS`, so it takes the same
     * `matchDeepLinkComprehensive` route a real launch does.
     *
     * This covers the graph half. The manifest half -- that the intent filter still advertises the
     * URI `SearchRoute` declares -- is `SearchDeepLinkManifestTest`, because the two are separate
     * declarations of one string and nothing but a test holds them together.
     */
    @Test
    fun searchDeepLink_navigatesToSearch() {
        launchApp()

        val handled = composeTestRule.runOnIdle {
            navController.handleDeepLink(
                Intent(Intent.ACTION_VIEW, SEARCH_DEEP_LINK_URI_PATTERN.toUri())
            )
        }

        assertTrue("the graph did not match $SEARCH_DEEP_LINK_URI_PATTERN", handled)
        waitUntilOnRoute(SearchRoute::class)
        assertOnRoute(SearchRoute::class)
        tab(UiR.string.search).assertIsSelected()
    }

    /**
     * A notification tap arrives as `pendingNoteId` and has to navigate *and* report itself
     * consumed -- without the second half the effect re-fires on every recomposition.
     */
    @Test
    fun pendingNoteId_navigatesToNotePreview_andIsConsumed() {
        launchApp(pendingNoteId = "note-from-notification")

        waitUntilOnRoute(NotePreviewRoute::class)
        // The id has to survive the trip: this is the whole payload of a notification tap, and the
        // old assertion -- that the Home tab was gone -- could not see it at all.
        assertEquals(
            "note-from-notification",
            composeTestRule.runOnIdle {
                navController.currentBackStackEntry?.toRoute<NotePreviewRoute>()?.noteId
            },
        )
        assertTrue(consumedPendingNoteId)
    }

    /** An incoming share opens the note composer, wherever the user happened to be. */
    @Test
    fun pendingShare_navigatesToTheNoteTab() {
        launchApp(hasPendingShare = true)

        waitUntilOnRoute(NoteRoute::class)
        assertOnRoute(NoteRoute::class)
        tab(R.string.note).assertIsSelected()
    }

    /** A share must not interrupt sign-in: the composer would be unusable with no account. */
    @Test
    fun pendingShare_doesNotNavigateAwayFromAuth() {
        launchApp(startDestination = AuthRoute, hasPendingShare = true)

        // Still on Auth, rather than merely "the Note tab is not showing" -- which was also true
        // of every other destination in the graph.
        assertOnRoute(AuthRoute::class)
    }
}
