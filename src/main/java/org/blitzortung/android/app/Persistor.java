package org.blitzortung.android.app;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.os.Vibrator;
import org.blitzortung.android.alarm.AlarmManager;
import org.blitzortung.android.alarm.AlarmParameters;
import org.blitzortung.android.alarm.factory.AlarmObjectFactory;
import org.blitzortung.android.app.controller.LocationHandler;
import org.blitzortung.android.app.controller.NotificationHandler;
import org.blitzortung.android.data.DataHandler;
import org.blitzortung.android.data.provider.DataResult;
import org.blitzortung.android.map.overlay.ParticipantsOverlay;
import org.blitzortung.android.map.overlay.StrokesOverlay;
import org.blitzortung.android.map.overlay.color.ParticipantColorHandler;
import org.blitzortung.android.map.overlay.color.StrokeColorHandler;

public class Persistor {

    private final DataHandler provider;

    private final AlarmManager alarmManager;

    private final StrokesOverlay strokesOverlay;

    private final ParticipantsOverlay participantsOverlay;

    private DataResult currentResult;

    private DataService dataService;

    public LocationHandler getLocationHandler() {
        return locationHandler;
    }

    private final LocationHandler locationHandler;

    public Persistor(Activity activity, SharedPreferences sharedPreferences, PackageInfo pInfo) {
        provider = new DataHandler(sharedPreferences, pInfo);
        locationHandler = new LocationHandler(activity, sharedPreferences);
        AlarmParameters alarmParameters = new AlarmParameters();
        alarmParameters.updateSectorLabels(activity);
        alarmManager = new AlarmManager(locationHandler, sharedPreferences, activity, (Vibrator) activity.getSystemService(Context.VIBRATOR_SERVICE), new NotificationHandler(activity), new AlarmObjectFactory(), alarmParameters);
        strokesOverlay = new StrokesOverlay(activity, new StrokeColorHandler(sharedPreferences));
        participantsOverlay = new ParticipantsOverlay(activity, new ParticipantColorHandler(sharedPreferences));
    }

    public void updateContext(Main mainActivity) {
        strokesOverlay.setActivity(mainActivity);
        participantsOverlay.setActivity(mainActivity);
        provider.setDataListener(mainActivity);

        alarmManager.updateContext(mainActivity);
        alarmManager.clearAlarmListeners();
        alarmManager.addAlarmListener(mainActivity);
    }

    public StrokesOverlay getStrokesOverlay() {
        return strokesOverlay;
    }

    public ParticipantsOverlay getParticipantsOverlay() {
        return participantsOverlay;
    }

    public DataHandler getDataHandler() {
        return provider;
    }

    public AlarmManager getAlarmManager() {
        return alarmManager;
    }

    public void setCurrentResult(DataResult result) {
        currentResult = result;
    }

    public DataResult getCurrentResult() {
        return currentResult;
    }

    public boolean hasCurrentResult() {
        return currentResult != null;
    }
    
    public boolean hasDataService() {
        return dataService != null;
    }

    public DataService getDataService() {
        return dataService;
    }

    public void setDataService(DataService dataService) {
        this.dataService = dataService;
    }
}
