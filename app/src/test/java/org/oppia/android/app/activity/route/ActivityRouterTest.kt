package org.oppia.android.app.activity.route

import android.app.Application
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.matcher.IntentMatchers.hasComponent
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.protobuf.MessageLite
import dagger.BindsInstance
import dagger.Component
import org.hamcrest.CoreMatchers.allOf
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.TypeSafeMatcher
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.oppia.android.app.activity.ActivityComponent
import org.oppia.android.app.activity.ActivityComponentFactory
import org.oppia.android.app.activity.ActivityIntentFactoriesModule
import org.oppia.android.app.application.ApplicationComponent
import org.oppia.android.app.application.ApplicationInjector
import org.oppia.android.app.application.ApplicationInjectorProvider
import org.oppia.android.app.application.ApplicationModule
import org.oppia.android.app.application.ApplicationStartupListenerModule
import org.oppia.android.app.application.testing.TestingBuildFlavorModule
import org.oppia.android.app.devoptions.DeveloperOptionsModule
import org.oppia.android.app.devoptions.DeveloperOptionsStarterModule
import org.oppia.android.app.home.recentlyplayed.RecentlyPlayedActivity
import org.oppia.android.app.model.DestinationScreen
import org.oppia.android.app.model.ProfileId
import org.oppia.android.app.model.RecentlyPlayedActivityParams
import org.oppia.android.app.model.RecentlyPlayedActivityTitle
import org.oppia.android.app.player.state.itemviewmodel.SplitScreenInteractionModule
import org.oppia.android.app.shim.ViewBindingShimModule
import org.oppia.android.app.testing.activity.TestActivity
import org.oppia.android.app.translation.testing.ActivityRecreatorTestModule
import org.oppia.android.data.backends.gae.NetworkConfigProdModule
import org.oppia.android.data.backends.gae.NetworkModule
import org.oppia.android.domain.classify.InteractionsModule
import org.oppia.android.domain.classify.rules.algebraicexpressioninput.AlgebraicExpressionInputModule
import org.oppia.android.domain.classify.rules.continueinteraction.ContinueModule
import org.oppia.android.domain.classify.rules.dragAndDropSortInput.DragDropSortInputModule
import org.oppia.android.domain.classify.rules.fractioninput.FractionInputModule
import org.oppia.android.domain.classify.rules.imageClickInput.ImageClickInputModule
import org.oppia.android.domain.classify.rules.itemselectioninput.ItemSelectionInputModule
import org.oppia.android.domain.classify.rules.mathequationinput.MathEquationInputModule
import org.oppia.android.domain.classify.rules.multiplechoiceinput.MultipleChoiceInputModule
import org.oppia.android.domain.classify.rules.numberwithunits.NumberWithUnitsRuleModule
import org.oppia.android.domain.classify.rules.numericexpressioninput.NumericExpressionInputModule
import org.oppia.android.domain.classify.rules.numericinput.NumericInputRuleModule
import org.oppia.android.domain.classify.rules.ratioinput.RatioInputModule
import org.oppia.android.domain.classify.rules.textinput.TextInputRuleModule
import org.oppia.android.domain.exploration.lightweightcheckpointing.ExplorationStorageModule
import org.oppia.android.domain.hintsandsolution.HintsAndSolutionConfigModule
import org.oppia.android.domain.hintsandsolution.HintsAndSolutionProdModule
import org.oppia.android.domain.onboarding.testing.ExpirationMetaDataRetrieverTestModule
import org.oppia.android.domain.oppialogger.LogStorageModule
import org.oppia.android.domain.oppialogger.LoggingIdentifierModule
import org.oppia.android.domain.oppialogger.analytics.ApplicationLifecycleModule
import org.oppia.android.domain.oppialogger.analytics.CpuPerformanceSnapshotterModule
import org.oppia.android.domain.oppialogger.logscheduler.MetricLogSchedulerModule
import org.oppia.android.domain.oppialogger.loguploader.LogReportWorkerModule
import org.oppia.android.domain.platformparameter.PlatformParameterModule
import org.oppia.android.domain.platformparameter.PlatformParameterSingletonModule
import org.oppia.android.domain.question.QuestionModule
import org.oppia.android.domain.topic.PrimeTopicAssetsControllerModule
import org.oppia.android.domain.workmanager.WorkManagerConfigurationModule
import org.oppia.android.testing.TestLogReportingModule
import org.oppia.android.testing.junit.InitializeDefaultLocaleRule
import org.oppia.android.testing.robolectric.RobolectricModule
import org.oppia.android.testing.threading.TestDispatcherModule
import org.oppia.android.testing.time.FakeOppiaClockModule
import org.oppia.android.util.accessibility.AccessibilityTestModule
import org.oppia.android.util.caching.AssetModule
import org.oppia.android.util.caching.testing.CachingTestModule
import org.oppia.android.util.extensions.getProtoExtra
import org.oppia.android.util.gcsresource.GcsResourceModule
import org.oppia.android.util.locale.LocaleProdModule
import org.oppia.android.util.logging.EventLoggingConfigurationModule
import org.oppia.android.util.logging.LoggerModule
import org.oppia.android.util.logging.SyncStatusModule
import org.oppia.android.util.logging.firebase.FirebaseLogUploaderModule
import org.oppia.android.util.networking.NetworkConnectionDebugUtilModule
import org.oppia.android.util.networking.NetworkConnectionUtilDebugModule
import org.oppia.android.util.parser.html.HtmlParserEntityTypeModule
import org.oppia.android.util.parser.image.GlideImageLoaderModule
import org.oppia.android.util.parser.image.ImageParsingModule
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowLog
import javax.inject.Singleton

private const val internalProfileId = 1

/** Tests for [ActivityRouter]. */
// FunctionName: test names are conventionally named with underscores.
@Suppress("FunctionName")
@RunWith(AndroidJUnit4::class)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(application = ActivityRouterTest.TestApplication::class)
class ActivityRouterTest {
  @get:Rule
  val initializeDefaultLocaleRule = InitializeDefaultLocaleRule()

  @get:Rule
  var activityRule =
    ActivityScenarioRule<TestActivity>(
      TestActivity.createIntent(ApplicationProvider.getApplicationContext())
    )

  @Before
  fun setUp() {
    Intents.init()
    setUpTestApplicationComponent()
  }

  @After
  fun tearDown() {
    Intents.release()
  }

  @Test
  fun testActivityRouter_canRouteToRecentlyPlayedActivity() {
    activityRule.scenario.onActivity { activity ->
      val activityRouter = activity.activityRouter
      val recentlyPlayedActivityParams =
        RecentlyPlayedActivityParams
          .newBuilder()
          .setProfileId(ProfileId.newBuilder().setInternalId(internalProfileId).build())
          .setActivityTitle(RecentlyPlayedActivityTitle.RECENTLY_PLAYED_STORIES).build()
      activityRouter.routeToScreen(
        DestinationScreen
          .newBuilder()
          .setRecentlyPlayedActivityParams(recentlyPlayedActivityParams)
          .build()
      )
      intended(
        allOf(
          hasProtoExtra(
            RecentlyPlayedActivity.RECENTLY_PLAYED_ACTIVITY_INTENT_EXTRAS_KEY,
            recentlyPlayedActivityParams
          ),
          hasComponent(RecentlyPlayedActivity::class.java.name)
        )
      )
    }
  }

  @Test
  fun testActivityRouter_destinationScreenNotSet_showsError() {
    activityRule.scenario.onActivity { activity ->
      val activityRouter = activity.activityRouter
      activityRouter.routeToScreen(DestinationScreen.getDefaultInstance())
      val log = ShadowLog.getLogs().last()
      assertThat(log.tag).isEqualTo("ActivityRouter")
      assertThat(log.msg).isEqualTo("Destination screen case is not identified.")
    }
  }

  private fun <T : MessageLite> hasProtoExtra(keyName: String, expectedProto: T): Matcher<Intent> {
    val defaultProto = expectedProto.newBuilderForType().build()
    return object : TypeSafeMatcher<Intent>() {
      override fun describeTo(description: Description) {
        description.appendText("Intent with extra: $keyName and proto value: $expectedProto")
      }

      override fun matchesSafely(intent: Intent): Boolean {
        return intent.hasExtra(keyName) &&
          intent.getProtoExtra(keyName, defaultProto) == expectedProto
      }
    }
  }

  private fun setUpTestApplicationComponent() {
    ApplicationProvider.getApplicationContext<TestApplication>().inject(this)
  }

  // TODO(#89): Move this to a common test application component.
  @Singleton
  @Component(
    modules = [
      RobolectricModule::class, TestDispatcherModule::class, ApplicationModule::class,
      PlatformParameterModule::class, LoggerModule::class, ContinueModule::class,
      FractionInputModule::class, ItemSelectionInputModule::class, MultipleChoiceInputModule::class,
      NumberWithUnitsRuleModule::class, NumericInputRuleModule::class, TextInputRuleModule::class,
      DragDropSortInputModule::class, ImageClickInputModule::class, InteractionsModule::class,
      GcsResourceModule::class, GlideImageLoaderModule::class, ImageParsingModule::class,
      HtmlParserEntityTypeModule::class, QuestionModule::class, TestLogReportingModule::class,
      AccessibilityTestModule::class, LogStorageModule::class, CachingTestModule::class,
      PrimeTopicAssetsControllerModule::class, ExpirationMetaDataRetrieverTestModule::class,
      ViewBindingShimModule::class, RatioInputModule::class, NetworkConfigProdModule::class,
      ApplicationStartupListenerModule::class, HintsAndSolutionConfigModule::class,
      LogReportWorkerModule::class, WorkManagerConfigurationModule::class,
      FirebaseLogUploaderModule::class, FakeOppiaClockModule::class,
      DeveloperOptionsStarterModule::class, DeveloperOptionsModule::class,
      ExplorationStorageModule::class, NetworkModule::class, HintsAndSolutionProdModule::class,
      NetworkConnectionUtilDebugModule::class, NetworkConnectionDebugUtilModule::class,
      AssetModule::class, LocaleProdModule::class, ActivityRecreatorTestModule::class,
      ActivityIntentFactoriesModule::class, PlatformParameterSingletonModule::class,
      NumericExpressionInputModule::class, AlgebraicExpressionInputModule::class,
      MathEquationInputModule::class, SplitScreenInteractionModule::class,
      LoggingIdentifierModule::class, ApplicationLifecycleModule::class,
      SyncStatusModule::class, MetricLogSchedulerModule::class, TestingBuildFlavorModule::class,
      EventLoggingConfigurationModule::class, ActivityRouterModule::class,
      CpuPerformanceSnapshotterModule::class
    ]
  )
  interface TestApplicationComponent : ApplicationComponent {
    @Component.Builder
    interface Builder {
      @BindsInstance
      fun setApplication(application: Application): Builder

      fun build(): TestApplicationComponent
    }

    fun inject(activityRouterTest: ActivityRouterTest)
  }

  class TestApplication : Application(), ActivityComponentFactory, ApplicationInjectorProvider {
    private val component: TestApplicationComponent by lazy {
      DaggerActivityRouterTest_TestApplicationComponent.builder()
        .setApplication(this)
        .build()
    }

    fun inject(activityRouterTest: ActivityRouterTest) {
      component.inject(activityRouterTest)
    }

    override fun createActivityComponent(activity: AppCompatActivity): ActivityComponent {
      return component.getActivityComponentBuilderProvider().get().setActivity(activity).build()
    }

    override fun getApplicationInjector(): ApplicationInjector = component
  }
}
