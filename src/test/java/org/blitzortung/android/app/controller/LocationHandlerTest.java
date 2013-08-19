package org.blitzortung.android.app.controller;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.location.LocationManager;
import com.google.common.collect.Lists;
import org.blitzortung.android.app.preference.PreferenceKey;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

@RunWith(RobolectricTestRunner.class)
public class LocationHandlerTest {
    
    @Mock
    private Context context;
    
    @Mock
    private SharedPreferences sharedPreferences;
    
    @Mock
    private LocationManager locationManager;

    @Mock
    private LocationHandler.Listener locationListener;
    
    private LocationHandler locationHandler;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        Application application = Robolectric.application;
        when(context.getSystemService(Context.LOCATION_SERVICE)).thenReturn(locationManager);
        when(context.getResources()).thenReturn(application.getResources());
        when(locationManager.getAllProviders()).thenReturn(Lists.newArrayList("network", "gps"));
        setLocationProviderPrefs(LocationManager.NETWORK_PROVIDER);
        
        locationHandler = new LocationHandler(context, sharedPreferences);
    }
    
    @Test
    public void testInitialization() {
        verify(sharedPreferences, times(1)).getString(PreferenceKey.LOCATION_MODE.toString(), LocationHandler.LocationProvider.NETWORK.getType());
        verify(sharedPreferences, times(1)).registerOnSharedPreferenceChangeListener(locationHandler);
        verify(locationManager, times(1)).addGpsStatusListener(locationHandler);
        verify(locationManager, times(1)).removeUpdates(locationHandler);
        verify(locationManager, times(1)).requestLocationUpdates(LocationHandler.LocationProvider.NETWORK.getType(), 10000, 10, locationHandler);       
    }

    @Test
    public void testSetUnavailableProvider() {
        when(sharedPreferences.getString(eq(PreferenceKey.LOCATION_MODE.toString()), anyString())).thenReturn(LocationHandler.LocationProvider.PASSIVE.getType());
        locationHandler.requestUpdates(locationListener);
        locationHandler.onSharedPreferenceChanged(sharedPreferences, PreferenceKey.LOCATION_MODE.toString());

        verify(sharedPreferences, times(2)).getString(PreferenceKey.LOCATION_MODE.toString(), LocationHandler.LocationProvider.NETWORK.getType());
        verify(sharedPreferences, times(1)).registerOnSharedPreferenceChangeListener(locationHandler);
        verify(locationListener, times(1)).onLocationChanged(null);
        verify(locationManager, times(1)).addGpsStatusListener(locationHandler);
        verify(locationManager, times(2)).removeUpdates(locationHandler);
        verify(locationManager, times(1)).requestLocationUpdates(anyString(), anyInt(), anyInt(), eq(locationHandler));
    }

    @Test
    public void testSetUnknownProvider() {
        when(sharedPreferences.getString(eq(PreferenceKey.LOCATION_MODE.toString()), anyString())).thenReturn("foo");
        locationHandler.requestUpdates(locationListener);
        locationHandler.onSharedPreferenceChanged(sharedPreferences, PreferenceKey.LOCATION_MODE.toString());

        verify(sharedPreferences, times(2)).getString(PreferenceKey.LOCATION_MODE.toString(), LocationHandler.LocationProvider.NETWORK.getType());
        verify(sharedPreferences, times(1)).registerOnSharedPreferenceChangeListener(locationHandler);
        verify(locationListener, times(1)).onLocationChanged(null);
        verify(locationManager, times(1)).addGpsStatusListener(locationHandler);
        verify(locationManager, times(2)).removeUpdates(locationHandler);
        verify(locationManager, times(1)).requestLocationUpdates(anyString(), anyInt(), anyInt(), eq(locationHandler));
    }
    
    private void setLocationProviderPrefs(String provider) {
        when(sharedPreferences.getString(PreferenceKey.LOCATION_MODE.toString(), LocationHandler.LocationProvider.NETWORK.getType())).thenReturn(provider);
    }
}
