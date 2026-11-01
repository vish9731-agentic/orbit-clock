package dev.orbit.clock.permissionprobe;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.Instrumentation;
import android.os.Bundle;

/** Test-only instrumentation runs under the target app UID, not adb's shell UID. */
public final class AlarmPermissionProbe extends Instrumentation {
    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        start();
    }

    @Override public void onStart() {
        Bundle result = new Bundle();
        AlarmManager manager = getTargetContext().getSystemService(AlarmManager.class);
        result.putBoolean("canScheduleExactAlarms", manager.canScheduleExactAlarms());
        finish(Activity.RESULT_OK, result);
    }
}
