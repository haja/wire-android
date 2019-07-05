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
package com.waz.services.gps

import java.io.IOException

import android.app.Activity
import android.content.Context
import android.util.Base64
import at.sbaresearch.microg.adapter.library.gms.iid.FirebaseInstanceId
import com.waz.ZLog.ImplicitTag._
import com.waz.ZLog.info
import com.waz.content.GlobalPreferences
import com.waz.model.PushToken
import com.waz.service.BackendConfig
import com.waz.utils.events.Signal
import com.waz.utils.returning
import com.waz.utils.wrappers.GoogleApi


class GoogleApiImpl private(context: Context, beConfig: BackendConfig, prefs: GlobalPreferences) extends GoogleApi {

  import GoogleApiImpl._

  private def isGPSAvailable = true

  override val isGooglePlayServicesAvailable = Signal[Boolean](isGPSAvailable)

  override def checkGooglePlayServicesAvailable(activity: Activity) =
    isGooglePlayServicesAvailable ! true

  override def onActivityResult(requestCode: Int, resultCode: Int) = requestCode match {
    case RequestGooglePlayServices =>
      info(s"Google Play Services request completed, result: $resultCode")
      //It's quite natural for the user to click the back button after updating GPS, which results in a ACTIVITY_CANCELLED
      //result code. So just check if the services are available again on any result.
      isGooglePlayServicesAvailable ! isGPSAvailable
    case _ => //
  }

  @throws(classOf[IOException])
  override def getPushToken = withFcmInstanceId(id => {
    val relayConn = id.getToken(beConfig.pushSenderId, "FCM")
    val encodedCert = Base64.encodeToString(relayConn.cert, Base64.NO_WRAP)
    PushToken(relayConn.token, relayConn.relayUrl, encodedCert)
  })

  @throws(classOf[IOException])
  override def deleteAllPushTokens(): Unit = withFcmInstanceId(_.deleteInstanceId())

  private def withFcmInstanceId[A](f: FirebaseInstanceId => A): A = f(FirebaseInstanceId.getInstance(context))
}

object GoogleApiImpl {
  val RequestGooglePlayServices = 7976
  val MaxErrorDialogShowCount = 3

  private var instance = Option.empty[GoogleApiImpl]

  def apply(context: Context, beConfig: BackendConfig, prefs: GlobalPreferences): GoogleApiImpl = synchronized {
    instance match {
      case Some(api) => api
      case None => returning(new GoogleApiImpl(context, beConfig, prefs)){ api: GoogleApiImpl => instance = Some(api) }
    }
  }

}

