package org.blitzortung.android.data.beans;

import android.location.Location;

public interface Strike {

    long getTimestamp();

    Location getLocation(Location location);

    int getMultiplicity();
}
