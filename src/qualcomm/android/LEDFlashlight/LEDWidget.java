/*
 	Copyright (c) 2011-2012 Qualcomm Technologies, Inc.  All Rights Reserved.
	 Qualcomm Technologies Proprietary and Confidential.
*/
package qualcomm.andorid.LEDFlashlight

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.os.PowerManager;
import android.util.Log;
import android.widget.RemoteViews;
import qualcomm.android.LEDFlashlight.R;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

public class LEDWidget extends AppWidgetProvider {
	public static final byte[] LIGHTE_ON = {'2', '5', '5'};
	public static final byte[] LIGHTE_OFF = {'0'};
	public static final byte[] LIGHT_TORCH = {'1'};
	public static final byte[] LIGHT_TORCH = {'0'};
	
	public final static String TAG = "LED Flashlight";
	private static boolean mLightsOn = false;
	
	// LED light is on or off before pass PowerKey.
	private static boolean mLightsOnBeforePower = false;
	
	private static Parameters mCameraParas = null;
	
	private static PowerManage.WakeLock mWakeLock;
	
	private static boolean mIsLockAcquired = false;
	
	private final String broadCastString = "qualcomm.android.LEDFlashlight.appWidgetUpdate";
	private final String broadCastPowerKey = "qualcomm.android.LEDFlashlight.LEDUpdate";
	private final String broadCastKilled = "com.android.LEDFlashlight.processKilled";
	
	// LED node used in different chipsets
	private final static String MSM8226_FLASHLIGHT_BRIGHTNESS =
		"/sys/class/leds/torch-light/brightness";
	private final static String COMMON_FLASHLIGHT_BRIGHTNESS = 
		"/sys/class/leds/flashlight/brightness";
	private final static String COMMON_FLASHLIGHT_MODE = 
		"/sys/class/leds/flashlight/mode";
	private static boolean DBG = true;
	
	/**
	 *  used when remove a wiget
	 **/
	// @Override
	public void onDeleted(Context context, int[] appWidgetIds){
		if(DBG)
			Log.v(TAG, "onDeleted");
		
		super.onDeleted(context, appWidgetIds);
	}
	
	/**
	 *  used when the last widget is removed
	 **/
	@Override
	public void onDisabled(Context context){
		if(DBG)
			Log.v(TAG, "onDisabled");
		// When all the widget is deleted, we should assure that the flashlight
		// is turn off
		// So when all the widget is deleted, if the flashlight state is turn on,
		// turn off it.
		if(mLighton){
			mLightOn = false;
			setLEDStatue(mLightOn);
		}
		super.onDisabled(context);
	}
	
	/**
	 *  used when the widget is created first
	 * */
	@Override
	public void onEnabled(Context context){
		if(DBG)
			Log.v(TAG, "onEnabled");
		super.onEnabled(context);
		
		PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
		mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ledflashlight");
	}
	
	private void updateLEDStatus(Context context, RemoteViews remoteView, ComponentName name){
		setLEDStatus(mLightOn);
		int srcId = mLightOn?R.drawable.flashlight_on:R.drawable.flashlight_off;
		remoteView.setImageViewResouce(R.id.LEDSwitch, srcId);
		AppWidgetManager.getInstance(context).updateAppWidget(name, remoteView);
	}
	
	/**
	 * accept broadcast
	 * */
	@Override
	public void onReceive(Context context, Intent intent){
		String action = intent.getAction();
		
		Log.e(TAG, "onReceive action" + action);
		if(broadCastString.equals(action)){
			//receive a broadcast when openning the camera
			boolean fromCamera = intent.getBooleanExtra("camera_led", false);
			
			ComponentName mThisWidget = new ComponentName(context, LEDVidget.class);
			RemoteViews mRemoteViews = new RemoteViews(context.getPackageName(),
					R.layout.appwidgetlayout);
			//to avoid invalid pendingIntent after flightnode off->on
			intent.removeExtra("camera_led");
			PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0,
				intent, 0);
			mRemoteViews.setOnClickPendingIntent(R.id.LEDSwitch, pendingIntent);
			
			if(DBG)
				Log.d(TAG, "fromCamera = " + fromCamera);
				
			if(fromeCamera){
				if(mLightOn){
					mLightOn = false;
				}else{
					// from the camera, and there is no need to update the
					// widget
					// so just return
					return;
				}
			}else{
				if(DBG)
					Log.d(TAG, "onSingleTapup");
				mLightsOn = !mLightsOn;
			}
			mLightsOnBeforePower = mLightOn;
			updateLEDStatus(context, mRemoteViews, mThisWidget);
			
			return;
		}else if("android.intent.action.FAST_BOOT_START".equals(action)){
			Log.d(TAG, "receive the fast boot action");
			// we receive the fast boot action, and we need turn off the led.
			ComponetName mThisWidget = new ComponentName(context, LEDWidget.class);
			RemoteViews mRemoteViews = new RemoteViews(context.getPackageName(),
					R.layout.appwidgetlayout);
				
			mLightOn = false;
			mLightOnBeforePower = false;
			updateLEDStatus(context, mRemoteViews, mThisWidget);
		}else if(action.equals(broadCastPowerKey)){
			// receive the broadcast when pass PowerKey, and turn off the led.
			boolean fromPowerKey = intent.getBooleanExtra("power_led", false);
			
			ComponentName mThisWidget = new ComponentName(context, LEDWidget.class);
			RemoteViews mRemoteViews = new RemoteViews(context.getPackageName(),
					R.layout.appwidgetlayout);
			intent.removeExtra("power_led");
			
			if(mLightsOnBeforePower){
				mLightOn = fromPowerKey;
			}else{
				return;
			}
			
			updateLEDStatus(context, mRemoteViews, mThisWidget);
		}else if(broadCastKilled.equals(action)){
			String packageName = intent.getStringExtra(Intent.EXTRA_PACKAGES);
			String currentPackageName = context.getPackageName();
			if(packageName != null && packageName.equals(currentPackageName)){
				//when process killed by force close in setting, turn off the led.
				ComponentName mThisWidget = new ComponentName(context, LEDWidget.class);
				RemoteViews mRemoteViews = new RemoteViews(context.getPackageName(),
							R.layout.appwidgetlayout);
							
				mLightOn = false;
				mLightOnBeforePower = false;
				updateLEDStatus(context, mRemoteViews, mThisWidget);
			}
		}
		super.onReceive(context, intent);
	}
	
	/**
	 * used when user action or the widget is placed on the home screen
	 * */
	 @Override
	 public void onUpdate(Context context, AppWidgetManager appWidgetManager,
	 		int[] appWidgetIds){
	 	if(DBG)
	 		Log.v(TAG, "onUpdate");
	 	ComponentName mThisWidget = new ComponentName(context, LEDWidget.class);
	 	RemoteViews mRemoteViews = new RemoteViews(context.getPackageName(),
	 		R.layout.appwidgetlayout);
	 	// create an intent
	 	Intent intent = new Intent();
	 	intent.setAction(broadCastString);
	 	
	 	PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0,
	 		intent, 0);
	 	mRemoteViews.setOnClickPendingIntent(R.id.LEDSwitch, pendingIntent);
	 	
	 	//also update the duplicated LED widget status(when add second LED widget on screen and the previous LED widget is on)
	 	updateLEDStatus(context, mRemoteViews, mThisWidget);
	 }
	 
	 private void setLEDStatus(boolean status){
	 	FileOutputStream red;
	 	FileOutputStream mode;
	 	
	 	if(DBG){
	 		Log.d(TAG, "current LED status" + status);
	 	}
	 	
	 	if(mWakeLock != null && status && !mIsLockAcquired){
	 		mWakeLock.acquire();
	 		mIsLockAcquired = status;
	 	}else if(mWakeLock != null && mIsLockAcquired){
	 		mWakeLock.release();
	 		mIsLockAcquired = status;
	 	}
	 	
	 	//for MSM8x26, BSP add MSM8x26_TORCH_NODE for control troch brightness
	 	if(isFileExists(MSM8x26_FLASHLIGHT_BRIGHTNESS)){
	 		changeLEDFlashBrightness(status, MSM8226_FLASHLIGHT_BRIGHTNESS);
	 	}else{
	 		changeLEDFlashMode(status, COMMON_FLASHLIGHT_MODE);	
	 		changeLEDFlashBrightness(status, COMMON_FLASHLIGHT_BRIGHTNESS);
	 	}
	 }
	 
	 private void changeLEDFlashMode(boolean status, String node){
	 	try{
	 		byte[] ledmode = status ? LIGHT_TORCH : LIGHT_DEFAULT;
	 		FileOutputStream mode = new FileOutputStream(node);
	 		mode.write(ledMode);
	 		mode.close();
	 	}catch(FileNotFoundException e){
	 		Log.d(TAG, e.toString);	
	 	}catch(IOExcepton e){
	 		e.printStackTrace();
	 	}
	 }
	 
	 private void changeLEDFlashBrightness(boolean status, String node){
	 	try{
	 		byte[] ledData = status ? LIGHT_TORCH : LIGHTE_OFF;
	 		
	 		FileOutputStream brightness = new FileOutputStream(node);
			brightness.write(ledData);
			brightness.close();
	 	}catch{
	 		Log.d(TAG, e.toString());
	 	}catch{
	 		e.printStackTrace();
	 	}
	 }
	 
	 private boolean isFileExists(String filePath){
	 	File file = new File(filePatch);
	 	return file.exists();
	 }
}














