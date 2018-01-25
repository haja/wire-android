/**
 * Wire
 * Copyright (C) 2018 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.waz.zclient.integrations

import android.content.Context
import android.os.Bundle
import android.support.annotation.Nullable
import android.support.v4.app.{Fragment, FragmentManager, FragmentPagerAdapter}
import android.support.v4.view.ViewPager
import android.util.AttributeSet
import android.view.animation.Animation
import android.view._
import android.widget.{ImageView, Toast}
import com.waz.model.{IntegrationId, ProviderId}
import com.waz.threading.Threading
import com.waz.utils.events.Signal
import com.waz.utils.returning
import com.waz.zclient.common.controllers.IntegrationsController
import com.waz.zclient.common.views.ImageAssetDrawable
import com.waz.zclient.common.views.ImageAssetDrawable.{RequestBuilder, ScaleType}
import com.waz.zclient.common.views.ImageController.{ImageSource, WireImage}
import com.waz.zclient.controllers.navigation.{INavigationController, Page}
import com.waz.zclient.integrations.IntegrationDetailsViewPager._
import com.waz.zclient.pages.main.pickuser.controller.IPickUserController
import com.waz.zclient.pages.main.pickuser.controller.IPickUserController.Destination
import com.waz.zclient.ui.text.{GlyphTextView, TypefaceTextView}
import com.waz.zclient.usersearch.PickUserFragment
import com.waz.zclient.utils.ContextUtils.{getDimenPx, getInt}
import com.waz.zclient.utils.{ContextUtils, RichView}
import com.waz.zclient.views.DefaultPageTransitionAnimation
import com.waz.zclient.{FragmentHelper, OnBackPressedListener, R, ViewHelper}

class IntegrationDetailsFragment extends FragmentHelper with OnBackPressedListener {
  implicit def ctx: Context = getActivity

  private lazy val integrationDetailsController = inject[IntegrationDetailsController]
  private lazy val integrationsController = inject[IntegrationsController]

  private lazy val providerId = ProviderId(getArguments.getString(IntegrationDetailsFragment.ProviderId))
  private lazy val integrationId = IntegrationId(getArguments.getString(IntegrationDetailsFragment.IntegrationId))

  private lazy val pictureAssetId = integrationDetailsController.currentIntegration.map(_.asset)
  private lazy val picture: Signal[ImageSource] = pictureAssetId.collect{ case Some(pic) => WireImage(pic) }
  private lazy val drawable = new ImageAssetDrawable(picture, scaleType = ScaleType.CenterInside, request = RequestBuilder.Regular, background = Some(ContextUtils.getDrawable(R.drawable.services)))

  private lazy val viewPager = view[IntegrationDetailsViewPager](R.id.view_pager)

  private lazy val nameView = returning(view[TypefaceTextView](R.id.integration_name)){ nv =>
    integrationDetailsController.currentIntegration.map(_.name).onUi { name => nv.foreach(_.setText(name)) }
  }
  private lazy val summaryView = returning(view[TypefaceTextView](R.id.integration_summary)){ sv =>
    integrationDetailsController.currentIntegration.map(_.summary).onUi { summary => sv.foreach(_.setText(summary)) }
  }
  private lazy val title = returning(view[TypefaceTextView](R.id.integration_title)) { title =>
    integrationDetailsController.currentIntegration.onUi { data => title.foreach(_.setText(data.name.toUpperCase)) }
  }

  override def onCreateAnimation(transit: Int, enter: Boolean, nextAnim: Int): Animation = {
    if (nextAnim == 0)
      super.onCreateAnimation(transit, enter, nextAnim)
    else if (enter)
      new DefaultPageTransitionAnimation(0,
        getDimenPx(R.dimen.open_new_conversation__thread_list__max_top_distance),
        enter,
        getInt(R.integer.framework_animation_duration_long),
        getInt(R.integer.framework_animation_duration_medium),
        1f)
    else
      new DefaultPageTransitionAnimation(
        0,
        getDimenPx(R.dimen.open_new_conversation__thread_list__max_top_distance),
        enter,
        getInt(R.integer.framework_animation_duration_medium),
        0,
        1f)
  }

  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)
    integrationDetailsController.currentIntegrationId ! (providerId, integrationId)
    integrationDetailsController.onAddServiceClick { _ =>

      integrationDetailsController.addingToConversation.fold{
        viewPager.foreach(_.goToConversations)
      } { conv =>
        integrationsController.addBot(conv, providerId, integrationId).map {
          case Left(e) =>
            Toast.makeText(getContext, s"Bot error: $e", Toast.LENGTH_SHORT).show()
            close()
          case Right(_) =>
            close()
        } (Threading.Ui)
      }
    }
  }

  override def onCreateView(inflater: LayoutInflater, viewContainer: ViewGroup, savedInstanceState: Bundle): View = {
    val localInflater =
      if (integrationDetailsController.addingToConversation.isEmpty)
        inflater.cloneInContext(new ContextThemeWrapper(getActivity, R.style.Theme_Dark))
      else
        inflater

    localInflater.inflate(R.layout.fragment_integration_details, viewContainer, false)
  }

  override def onViewCreated(v: View, @Nullable savedInstanceState: Bundle): Unit = {
    super.onViewCreated(v, savedInstanceState)

    view[GlyphTextView](R.id.integration_close).foreach { _.onClick(close()) }
    view[GlyphTextView](R.id.integration_back).foreach { _.onClick(goBack()) }
    view[ImageView](R.id.integration_picture).foreach { _.setImageDrawable(drawable) }

    title
    summaryView
    nameView

    viewPager.setAdapter(IntegrationDetailsAdapter(getChildFragmentManager, providerId, integrationId))
  }

  override def onBackPressed(): Boolean = goBack()

  def goBack(): Boolean = {
    viewPager.getCurrentItem match {
      case IntegrationDetailsViewPager.ConvListPage =>
        viewPager.goToDetails
      case _ =>
        getFragmentManager.popBackStack()
        if (integrationDetailsController.addingToConversation.nonEmpty) {
          inject[INavigationController].setRightPage(Page.PICK_USER, IntegrationDetailsFragment.Tag)
        } else {
          inject[INavigationController].setLeftPage(Page.PICK_USER, IntegrationDetailsFragment.Tag)
        }
    }
    true
  }

  def close(): Boolean = {
    getFragmentManager.popBackStack(PickUserFragment.TAG, FragmentManager.POP_BACK_STACK_INCLUSIVE)
    if (integrationDetailsController.addingToConversation.nonEmpty) {
      inject[IPickUserController].hidePickUser(Destination.PARTICIPANTS)
      inject[INavigationController].setRightPage(Page.PARTICIPANT, IntegrationDetailsFragment.Tag)
    } else {
      inject[IPickUserController].hidePickUser(Destination.CONVERSATION_LIST)
      inject[INavigationController].setLeftPage(Page.CONVERSATION_LIST, IntegrationDetailsFragment.Tag)
    }
    true
  }
}

object IntegrationDetailsFragment {

  val Tag = classOf[IntegrationDetailsFragment].getName
  val IntegrationId = "ARG_INTEGRATION_ID"
  val ProviderId = "ARG_PROVIDER_ID"

  def newInstance(providerId: ProviderId, integrationId: IntegrationId) = returning(new IntegrationDetailsFragment) {
    _.setArguments(returning(new Bundle){ b =>
      b.putString(ProviderId, providerId.str)
      b.putString(IntegrationId, integrationId.str)
    })
  }
}

object IntegrationDetailsViewPager {
  val DetailsPage = 0
  val ConvListPage = 1
}

case class IntegrationDetailsViewPager (context: Context, attrs: AttributeSet) extends ViewPager(context, attrs) with ViewHelper {
  def this(context: Context) = this(context, null)

  def goToDetails = setCurrentItem(DetailsPage)
  def goToConversations = setCurrentItem(ConvListPage)

  override def onInterceptTouchEvent(ev: MotionEvent): Boolean = false
  override def onTouchEvent(ev: MotionEvent): Boolean = false
}

case class IntegrationDetailsAdapter(fm: FragmentManager, providerId: ProviderId, integrationId: IntegrationId) extends FragmentPagerAdapter(fm) {

  override def getItem(position: Int): Fragment = {
    position match {
      case DetailsPage =>
        new IntegrationDetailsSummaryFragment()
      case ConvListPage =>
        new IntegrationConversationSearchFragment()
    }
  }

  override def getCount: Int = 2
}
