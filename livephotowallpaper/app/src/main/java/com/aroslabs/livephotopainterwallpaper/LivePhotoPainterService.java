package com.aroslabs.livephotopainterwallpaper;

import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.view.MotionEvent;
import android.view.SurfaceHolder;

import com.aroslabs.livephotopainterwallpaper.Engine.GLWallpaperService;

/**
 * Created by Ryan on 1/22/2017.
 */
public class LivePhotoPainterService extends GLWallpaperService {

    public LivePhotoPainterService() {
        super();
    }

    public Engine onCreateEngine() {
        MyEngine engine = new MyEngine();
        return engine;
    }

    class MyEngine extends GLWallpaperService.GLEngine implements
            SharedPreferences.OnSharedPreferenceChangeListener,
            SensorEventListener {
        LivePhotoPainterRenderer renderer;

        public MyEngine() {
            super();
            // handle prefs, other initialization
            renderer = new LivePhotoPainterRenderer(getApplicationContext());
            setRenderer(renderer);
            //TODO: set to dirty
            setRenderMode(RENDERMODE_CONTINUOUSLY);
        }

        public void onDestroy() {
            // Unregister this as listener
            sm.unregisterListener(this);

            // Kill renderer
            if (renderer != null) {
                renderer.release(); // assuming yours has this method - it
                // should!
            }
            renderer = null;

            setTouchEventsEnabled(false);

            super.onDestroy();
        }

        private SensorManager sm;

        @Override
        public void onTouchEvent(MotionEvent event) {
            super.onTouchEvent(event);
            renderer.onTouchEvent(event);
        }

        @Override
        public void onCreate(SurfaceHolder surfaceHolder) {
            super.onCreate(surfaceHolder);

            if (GLWallpaperService.DEBUG) {
                android.os.Debug.waitForDebugger();
            }

            // Add touch events
            setTouchEventsEnabled(true);

            // Get sensormanager and register as listener.
            sm = (SensorManager) getSystemService(SENSOR_SERVICE);
            Sensor orientationSensor = sm
                    .getDefaultSensor(SensorManager.SENSOR_ORIENTATION);
            sm.registerListener(this, orientationSensor,
                    SensorManager.SENSOR_DELAY_GAME);
        }

        public void onSharedPreferenceChanged(
                SharedPreferences sharedPreferences, String key) {
        }

        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }

        public void onSensorChanged(SensorEvent event) {
            // /renderer.onSensorChanged(event);
        }
    }

}
