package com.github.shadowsocks

import android.content.{ComponentName, Context, Intent, ServiceConnection}
import android.os.{RemoteException, IBinder}
import com.github.shadowsocks.aidl.{IShadowsocksServiceCallback, IShadowsocksService}
import com.github.shadowsocks.utils.Action
import com.github.shadowsocks.ShadowsocksApplication.app

/**
  * @author Mygod
  */
trait ServiceBoundContext extends Context with IBinder.DeathRecipient {
  class ShadowsocksServiceConnection extends ServiceConnection {
    override def onServiceConnected(name: ComponentName, service: IBinder) {
      binder = service
      service.linkToDeath(ServiceBoundContext.this, 0)
      bgService = IShadowsocksService.Stub.asInterface(service)
      if (callback != null && !callbackRegistered) try {
        bgService.registerCallback(callback)
        callbackRegistered = true
        if (listeningForBandwidth) bgService.startListeningForBandwidth(callback)
      } catch {
        case _: RemoteException => // Nothing
      }
      ServiceBoundContext.this.onServiceConnected()
    }
    override def onServiceDisconnected(name: ComponentName) {
      unregisterCallback
      ServiceBoundContext.this.onServiceDisconnected()
      bgService = null
      binder = null
    }
  }

  def setListeningForBandwidth(value: Boolean) {
    if (listeningForBandwidth != value && bgService != null && callback != null)
      if (value) bgService.startListeningForBandwidth(callback) else bgService.stopListeningForBandwidth(callback)
    listeningForBandwidth = value
  }

  private def unregisterCallback() {
    if (bgService != null && callback != null && callbackRegistered) try bgService.unregisterCallback(callback) catch {
      case ignored: RemoteException =>
    }
    listeningForBandwidth = false
    callbackRegistered = false
  }

  def onServiceConnected() = ()
  def onServiceDisconnected() = ()
  override def binderDied = ()

  private var callback: IShadowsocksServiceCallback.Stub = _
  private var connection: ShadowsocksServiceConnection = _
  private var callbackRegistered: Boolean = _
  private var listeningForBandwidth: Boolean = _

  // Variables
  var binder: IBinder = _
  var bgService: IShadowsocksService = _

  def attachService(callback: IShadowsocksServiceCallback.Stub = null) {
    this.callback = callback
    if (bgService == null) {
      val s = if (app.isNatEnabled) classOf[ShadowsocksNatService] else classOf[ShadowsocksVpnService]

      val intent = new Intent(this, s)
      intent.setAction(Action.SERVICE)

      connection = new ShadowsocksServiceConnection()
      bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }
  }

  def detachService() {
    unregisterCallback
    callback = null
    if (connection != null) {
      try unbindService(connection) catch {
        case _: IllegalArgumentException => // ignore
      }
      connection = null
    }
    if (binder != null) {
      binder.unlinkToDeath(this, 0)
      binder = null
    }
    bgService = null
  }
}
