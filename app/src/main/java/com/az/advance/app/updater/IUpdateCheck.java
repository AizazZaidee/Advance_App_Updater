package com.az.advance.app.updater;

import android.content.Context;
import android.content.pm.PackageManager;

/**
 * Created by Aizaz on 2014-12-16.
 */
public interface IUpdateCheck {
    void checkForUpdate(final Context context, final String updateURL, final boolean onlyWifi, final UpdateCheck.UpdateCheckCallback updateCheckResult);
    int getAppVersionCode(final Context context) throws PackageManager.NameNotFoundException;
}
