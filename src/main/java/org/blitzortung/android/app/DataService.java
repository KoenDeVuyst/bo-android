package org.blitzortung.android.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.*;
import android.preference.PreferenceManager;
import android.util.Log;
import org.blitzortung.android.alarm.AlarmParameters;
import org.blitzortung.android.alarm.factory.AlarmObjectFactory;
import org.blitzortung.android.app.controller.LocationHandler;
import org.blitzortung.android.app.controller.NotificationHandler;
import org.blitzortung.android.app.view.PreferenceKey;
import org.blitzortung.android.data.DataChannel;
import org.blitzortung.android.data.DataHandler;
import org.blitzortung.android.util.Period;

import java.util.HashSet;
import java.util.Set;

public class DataService extends Service implements Runnable, SharedPreferences.OnSharedPreferenceChangeListener {

    public static final String RETRIEVE_DATA_ACTION = "retrieveData";

    public static final String WAKE_LOCK_TAG = "boAndroidWakeLock";

    private final Handler handler;

    private int period;

    private int backgroundPeriod;

    private final Period updatePeriod;

    private boolean alarmEnabled;

    private boolean backgroundOperation;

    private boolean updateParticipants;

    private boolean enabled;

    private DataHandler dataHandler;

    private DataServiceStatusListener listener;

    private final IBinder binder = new DataServiceBinder();

    private AlarmManager alarmManager;
    
    private org.blitzortung.android.alarm.AlarmManager alarmProcessor;

    private PendingIntent pendingIntent;

    private PowerManager.WakeLock wakeLock;

    @SuppressWarnings("UnusedDeclaration")
    public DataService() {
        this(new Handler(), new Period());
        Log.d(Main.LOG_TAG, "DataService() created with new handler");
    }

    protected DataService(Handler handler, Period updatePeriod) {
        Log.d(Main.LOG_TAG, "DataService() create");
        this.handler = handler;
        this.updatePeriod = updatePeriod;
    }

    public int getPeriod() {
        return period;
    }

    public int getBackgroundPeriod() {
        return backgroundPeriod;
    }

    public long getLastUpdate() {
        return updatePeriod.getLastUpdateTime();
    }

    public boolean isInBackgroundOperation() {
        return backgroundOperation;
    }

    public void reloadData() {
        if (isEnabled()) {
            restart();
        } else {
            Set<DataChannel> updateTargets = new HashSet<DataChannel>();
            updateTargets.add(DataChannel.STROKES);
            dataHandler.updateData(updateTargets);
        }
    }

    public class DataServiceBinder extends Binder {
        DataService getService() {
            Log.d(Main.LOG_TAG, "DataServiceBinder.getService() " + DataService.this);
            return DataService.this;
        }
    }

    public interface DataServiceStatusListener {
        public void onDataServiceStatusUpdate(String dataServiceStatus);
    }

    @Override
    public void onCreate() {
        Log.i(Main.LOG_TAG, "DataService.onCreate()");
        super.onCreate();

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        updateSharedPreferences(preferences);
    }

    @Override
    public void onDestroy() {
        Log.i(Main.LOG_TAG, "DataService.onDestroy()");
        super.onDestroy();

        handler.removeCallbacks(this);
    }

    public void updateSharedPreferences(SharedPreferences preferences) {
        preferences.registerOnSharedPreferenceChangeListener(this);

        onSharedPreferenceChanged(preferences, PreferenceKey.QUERY_PERIOD);
        onSharedPreferenceChanged(preferences, PreferenceKey.ALARM_ENABLED);
        onSharedPreferenceChanged(preferences, PreferenceKey.BACKGROUND_QUERY_PERIOD);
        onSharedPreferenceChanged(preferences, PreferenceKey.SHOW_PARTICIPANTS);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(Main.LOG_TAG, "DataService.onStartCommand() startId: " + startId + " " + intent);

        if (intent != null) {
            if (intent.getFlags() == Intent.FLAG_ACTIVITY_NEW_TASK) {
                SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
                int backgroundPeriod = Integer.valueOf(sharedPreferences.getString(PreferenceKey.BACKGROUND_QUERY_PERIOD.toString(), "0"));
                if (backgroundPeriod > 0) {
                    Log.v(Main.LOG_TAG, "DataService.onStartCommand() start service without main app");
                    PackageInfo packageInfo = null;
                    try {
                        packageInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
                    } catch (PackageManager.NameNotFoundException e) {
                        throw new IllegalStateException(e);
                    }
                    if (packageInfo != null) {
                        dataHandler = new DataHandler(sharedPreferences, packageInfo);
                        LocationHandler locationHandler = new LocationHandler(this, sharedPreferences);
                        alarmProcessor = new org.blitzortung.android.alarm.AlarmManager(locationHandler, sharedPreferences, this, (Vibrator) this.getSystemService(Context.VIBRATOR_SERVICE), new NotificationHandler(new Main()), new AlarmObjectFactory(), new AlarmParameters());
                        onResume();
                    }
                } else {
                    Log.v(Main.LOG_TAG, "DataService.onStartCommand() do not start service");
                }
                
            } else  if (RETRIEVE_DATA_ACTION.equals(intent.getAction())) {
                if (!backgroundOperation) {
                    discardAlarm();
                } else {
                    PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
                    wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG);
                    wakeLock.acquire(20000);

                    Log.v(Main.LOG_TAG, "DataService.onStartCommand() acquire wake lock " + wakeLock);

                    handler.removeCallbacks(this);
                    handler.post(this);
                }
            }
        }

        return START_STICKY;
    }

    public void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) {
            Log.v(Main.LOG_TAG, "DataService.releaseWakeLock() " + wakeLock);
            wakeLock.release();
        }
        wakeLock = null;
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.i(Main.LOG_TAG, "DataService.onBind() " + intent);
        return binder;
    }

    @Override
    public void run() {

        if (backgroundOperation) {
            Log.v(Main.LOG_TAG, "DataService.run() in background");

            dataHandler.updateDatainBackground();
        } else {
            long currentTime = Period.getCurrentTime();
            if (dataHandler != null) {
                Set<DataChannel> updateTargets = new HashSet<DataChannel>();

                int currentPeriod = getCurrentPeriod();

                if (updatePeriod.shouldUpdate(currentTime, currentPeriod)) {
                    updatePeriod.setLastUpdateTime(currentTime);
                    updateTargets.add(DataChannel.STROKES);

                    if (!backgroundOperation && updateParticipants && updatePeriod.isNthUpdate(10)) {
                        updateTargets.add(DataChannel.PARTICIPANTS);
                    }
                }

                if (!updateTargets.isEmpty()) {
                    dataHandler.updateData(updateTargets);
                }

                if (!backgroundOperation && listener != null) {
                    listener.onDataServiceStatusUpdate(String.format("%d/%ds", updatePeriod.getCurrentUpdatePeriod(currentTime, currentPeriod), currentPeriod));
                }
            }
            // Schedule the next update
            handler.postDelayed(this, 1000);
        }
    }

    private int getCurrentPeriod() {
        return backgroundOperation ? backgroundPeriod : period;
    }

    public void restart() {
        updatePeriod.restart();
    }

    public void onResume() {
        backgroundOperation = false;

        discardAlarm();

        if (dataHandler.isRealtime()) {
            Log.v(Main.LOG_TAG, "DataService.onResume() enable");
            enable();
        } else {
            Log.v(Main.LOG_TAG, "DataService.onResume() do not enable");
        }
    }

    public boolean onPause() {
        backgroundOperation = true;

        handler.removeCallbacks(this);
        Log.v(Main.LOG_TAG, "DataService.onPause() remove callback");

        if (alarmEnabled && backgroundPeriod != 0) {
            createAlarm();
            return false;
        }
        return true;
    }

    public void setListener(DataServiceStatusListener listener) {
        this.listener = listener;
    }

    public void enable() {
        handler.removeCallbacks(this);
        handler.post(this);
        enabled = true;
    }

    public boolean isEnabled() {
        return enabled;
    }

    protected void disable() {
        enabled = false;
        handler.removeCallbacks(this);
    }

    public void setDataHandler(DataHandler dataHandler) {
        this.dataHandler = dataHandler;
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String keyString) {
        onSharedPreferenceChanged(sharedPreferences, PreferenceKey.fromString(keyString));
    }

    private void onSharedPreferenceChanged(SharedPreferences sharedPreferences, PreferenceKey key) {
        switch (key) {
            case QUERY_PERIOD:
                period = Integer.parseInt(sharedPreferences.getString(key.toString(), "60"));
                break;

            case BACKGROUND_QUERY_PERIOD:
                backgroundPeriod = Integer.parseInt(sharedPreferences.getString(key.toString(), "0"));
                Log.v(Main.LOG_TAG, String.format("DataService.onSharedPreferenceChanged() backgroundPeriod=%d", backgroundPeriod));
                break;

            case SHOW_PARTICIPANTS:
                updateParticipants = sharedPreferences.getBoolean(key.toString(), true);
                break;

            case ALARM_ENABLED:
                alarmEnabled = sharedPreferences.getBoolean(key.toString(), false);
                break;
        }
    }

    private void createAlarm() {
        discardAlarm();

        Log.v(Main.LOG_TAG, String.format("DataService.createAlarm() %d", backgroundPeriod));
        Intent intent = new Intent(this, DataService.class);
        intent.setAction(RETRIEVE_DATA_ACTION);
        pendingIntent = PendingIntent.getService(this, 0, intent, 0);
        alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
        if (alarmManager != null) {
            alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, 0, backgroundPeriod * 1000, pendingIntent);
        } else {
            Log.e(Main.LOG_TAG, "DataService.createAlarm() failed");
        }
    }

    private void discardAlarm() {
        releaseWakeLock();

        if (alarmManager != null) {
            Log.v(Main.LOG_TAG, "DataService.discardAlarm()");
            alarmManager.cancel(pendingIntent);
            pendingIntent.cancel();

            pendingIntent = null;
            alarmManager = null;
        }
    }

}
