/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.sunshine.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.content.LocalBroadcastManager;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.Time;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import java.lang.ref.WeakReference;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

;

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
public class SunshineWatchFace extends CanvasWatchFaceService {
    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);

    /**
     * Update rate in milliseconds for interactive mode. We update once a second since seconds are
     * displayed in interactive mode.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<SunshineWatchFace.Engine> mWeakReference;

        public EngineHandler(SunshineWatchFace.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            SunshineWatchFace.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

    private class Engine extends CanvasWatchFaceService.Engine {
        final Handler mUpdateTimeHandler = new EngineHandler(this);
        boolean mRegisteredTimeZoneReceiver = false;
        Paint mBackgroundPaint;

        Bitmap mBackgroundBitmap;
        Bitmap mIconBitmap;

        Paint mTextPaint;
        Paint mHighTempPaint;
        Paint mLowTempPaint;

        int weatherId = 800;
        String mHighTemp = "";
        String mLowTemp = "";

        boolean mAmbient;
        Time mTime;
        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mTime.clear(intent.getStringExtra("time-zone"));
                mTime.setToNow();
            }
        };
        float mXOffset;
        float mYOffset;

        float mHighTempXOffset;
        float mHighTempYOffset;
        float mLowTempXOffset;
        float mLowTempYOffset;

        float mWeatherIconXSize;
        float mWeatherIconYSize;
        float mWeatherIconXOffset;
        float mWeatherIconYOffset;

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            IntentFilter messageFilter = new IntentFilter(Intent.ACTION_SEND);
            MessageReceiver messageReceiver = new MessageReceiver();
            LocalBroadcastManager.getInstance(getApplicationContext()).registerReceiver(messageReceiver, messageFilter);

            setWatchFaceStyle(new WatchFaceStyle.Builder(SunshineWatchFace.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .build());
            Resources resources = SunshineWatchFace.this.getResources();

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(resources.getColor(R.color.background));

            mBackgroundBitmap = BitmapFactory.decodeResource(getResources(),
                    getBackgroundResourceForWeatherCondition(weatherId));

            mIconBitmap = BitmapFactory.decodeResource(getResources(),
                    getArtResourceForWeatherCondition(weatherId));

            mTextPaint = new Paint();
            mTextPaint = createTextPaint(resources.getColor(R.color.white));

            mHighTempPaint = new Paint();
            mHighTempPaint = createTextPaint(resources.getColor(R.color.white));

            mLowTempPaint = new Paint();
            mLowTempPaint = createTextPaint(resources.getColor(R.color.white));

            //TODO Remove - Only temporary for testing purposes.
//            if (weatherId == 511 || weatherId >= 600 && weatherId <= 622 ||
//                    weatherId >= 701 && weatherId <= 761) {
//                mTextPaint.setColor(getResources().getColor(R.color.dark_grey));
//                mHighTempPaint.setColor(getResources().getColor(R.color.dark_grey));
//                mLowTempPaint.setColor(getResources().getColor(R.color.dark_grey));
//            } else {
//                mTextPaint.setColor(getResources().getColor(R.color.white));
//                mHighTempPaint.setColor(getResources().getColor(R.color.white));
//                mLowTempPaint.setColor(getResources().getColor(R.color.white));
//            }
            //TODO End of TODO

            mWeatherIconYOffset = resources.getDimension(R.dimen.weather_icon_y_offset);

            mTime = new Time();
        }

        public class MessageReceiver extends BroadcastReceiver {
            @Override
            public void onReceive(Context context, Intent intent) {
                weatherId = intent.getIntExtra("weatherId", 1);
                mHighTemp = intent.getStringExtra("high");
                mLowTemp = intent.getStringExtra("low");
                Log.d("onReceive", "Received weatherId: " + weatherId + " High: " + mHighTemp + " Low: " + mLowTemp);

                mIconBitmap = BitmapFactory.decodeResource(getResources(),
                        getArtResourceForWeatherCondition(weatherId));

                mBackgroundBitmap = BitmapFactory.decodeResource(getResources(),
                        getBackgroundResourceForWeatherCondition(weatherId));

                if (weatherId == 511 || weatherId >= 600 && weatherId <= 622 ||
                        weatherId >= 701 && weatherId <= 761) {
                    mTextPaint.setColor(getResources().getColor(R.color.dark_grey));
                    mHighTempPaint.setColor(getResources().getColor(R.color.dark_grey));
                    mLowTempPaint.setColor(getResources().getColor(R.color.dark_grey));
                } else {
                    mTextPaint.setColor(getResources().getColor(R.color.white));
                    mHighTempPaint.setColor(getResources().getColor(R.color.white));
                    mLowTempPaint.setColor(getResources().getColor(R.color.white));
                }
            }
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        private Paint createTextPaint(int textColor) {
            Paint paint = new Paint();
            paint.setColor(textColor);
            paint.setTypeface(NORMAL_TYPEFACE);
            paint.setAntiAlias(true);
            return paint;
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();

                // Update time zone in case it changed while we weren't visible.
                mTime.clear(TimeZone.getDefault().getID());
                mTime.setToNow();
            } else {
                unregisterReceiver();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            SunshineWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            SunshineWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            Resources resources = SunshineWatchFace.this.getResources();
            boolean isRound = insets.isRound();
            mXOffset = resources.getDimension(isRound
                    ? R.dimen.digital_x_offset_round : R.dimen.digital_x_offset);
            mHighTempXOffset = resources.getDimension(isRound
                    ? R.dimen.highTemp_x_offset_round : R.dimen.highTemp_x_offset);
            mLowTempXOffset = resources.getDimension(isRound
                    ? R.dimen.lowTemp_x_offset_round : R.dimen.lowTemp_x_offset);
            float textSize = resources.getDimension(isRound
                    ? R.dimen.digital_text_size_round : R.dimen.digital_text_size);
            float highTextSize = resources.getDimension(isRound
                    ? R.dimen.highTemp_text_size_round : R.dimen.highTemp_text_size);
            float lowTextSize = resources.getDimension(isRound
                    ? R.dimen.lowTemp_text_size_round : R.dimen.lowTemp_text_size);

            mTextPaint.setTextSize(textSize);
            mHighTempPaint.setTextSize(highTextSize);
            mLowTempPaint.setTextSize(lowTextSize);
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;
                if (mLowBitAmbient) {
                    mTextPaint.setAntiAlias(!inAmbientMode);
                    mHighTempPaint.setAntiAlias(!inAmbientMode);
                    mLowTempPaint.setAntiAlias(!inAmbientMode);
                }
                invalidate();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            int width = bounds.width();
            int height = bounds.height();

            float centerX = width / 2f;
            float centerY = height / 2f;

            mWeatherIconXSize = ((float) (.8 * width));
            mWeatherIconYSize = mWeatherIconXSize;

            mWeatherIconXOffset = ((float) .2 * width) / 2;

            // Draw the background.
            if (isInAmbientMode()) {
                canvas.drawColor(Color.BLACK);
            } else {
                canvas.drawBitmap(mBackgroundBitmap, 0, 0, mBackgroundPaint);
                if (mIconBitmap != null) {
                    canvas.drawBitmap(getResizedBitmap(mIconBitmap,
                            mWeatherIconXSize,
                            mWeatherIconYSize),
                            mWeatherIconXOffset,
                            mWeatherIconYOffset,
                            mBackgroundPaint);
                }
            }

            // Draw H:MM in ambient mode or H:MM:SS in interactive mode.
            mTime.setToNow();
            String text = mAmbient
                    ? String.format("%d:%02d", mTime.hour, mTime.minute)
                    : String.format("%d:%02d:%02d", mTime.hour, mTime.minute, mTime.second);

            mYOffset = ((float) ((.5 * height) + (.5 * mTextPaint.getTextSize())));
            mHighTempYOffset = mYOffset + (mTextPaint.getTextSize()) - 20;
            mLowTempYOffset = mHighTempYOffset;

            canvas.drawText(text, mXOffset, mYOffset, mTextPaint);

            canvas.drawText(mHighTemp,
                    mHighTempXOffset,
                    mHighTempYOffset,
                    mHighTempPaint);
            canvas.drawText(mLowTemp,
                    mLowTempXOffset,
                    mLowTempYOffset,
                    mLowTempPaint);
        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }

    }

    /**
     * Helper method to provide the art resource id according to the weather condition id returned
     * by the OpenWeatherMap call.
     * @param weatherId from OpenWeatherMap API response
     * @return resource id for the corresponding icon. -1 if no relation is found.
     */
    public static int getArtResourceForWeatherCondition(int weatherId) {
        // Based on weather code data found at:
        // http://bugs.openweathermap.org/projects/api/wiki/Weather_Condition_Codes
        if (weatherId >= 200 && weatherId <= 232) {
            return R.drawable.art_storm;
        } else if (weatherId >= 300 && weatherId <= 321) {
            return R.drawable.art_light_rain;
        } else if (weatherId >= 500 && weatherId <= 504) {
            return R.drawable.art_rain;
        } else if (weatherId == 511) {
            return R.drawable.art_snow_3;
        } else if (weatherId >= 520 && weatherId <= 531) {
            return R.drawable.art_rain;
        } else if (weatherId >= 600 && weatherId <= 622) {
            return R.drawable.art_snow_3;
        } else if (weatherId >= 701 && weatherId <= 761) {
            return R.drawable.art_fog;
        } else if (weatherId == 761 || weatherId == 781) {
            return R.drawable.art_storm;
        } else if (weatherId == 800) {
            return R.drawable.art_clear;
        } else if (weatherId == 801) {
            return R.drawable.art_light_clouds;
        } else if (weatherId >= 802 && weatherId <= 804) {
            return R.drawable.art_clouds;
        }
        return -1;
    }

    /**
     * Helper method to provide the art resource id according to the weather condition id returned
     * by the OpenWeatherMap call.
     * @param weatherId from OpenWeatherMap API response
     * @return resource id for the corresponding icon. -1 if no relation is found.
     */
    public static int getBackgroundResourceForWeatherCondition(int weatherId) {
        // Update the background image to align with the weather condition.
        if (weatherId >= 200 && weatherId <= 232) {
            //return R.drawable.art_storm;
            return R.drawable.drk3_grey_bg;
        } else if (weatherId >= 300 && weatherId <= 321) {
            //return R.drawable.art_light_rain;
            return R.drawable.drk_grey_bg;
        } else if (weatherId >= 500 && weatherId <= 504) {
            //return R.drawable.art_rain;
            return R.drawable.drk3_grey_bg;
        } else if (weatherId == 511) {
            //return R.drawable.art_snow;
            return R.drawable.lgt_grey_bg;
        } else if (weatherId >= 520 && weatherId <= 531) {
            //return R.drawable.art_rain;
            return R.drawable.drk3_grey_bg;
        } else if (weatherId >= 600 && weatherId <= 622) {
            //return R.drawable.art_snow_3;
            return R.drawable.lgt_grey_bg;
        } else if (weatherId >= 701 && weatherId <= 761) {
            //return R.drawable.art_fog;
            return R.drawable.lgt_grey_bg;
        } else if (weatherId == 761 || weatherId == 781) {
            //return R.drawable.art_storm;
            return R.drawable.drk3_grey_bg;
        } else if (weatherId == 800) {
            //return R.drawable.art_clear;
            return R.drawable.blue_bg;
        } else if (weatherId == 801) {
            //return R.drawable.art_light_clouds;
            return R.drawable.blue_bg;
        } else if (weatherId >= 802 && weatherId <= 804) {
            //return R.drawable.art_clouds;
            return R.drawable.grey_bg;
        }
        return -1;
    }

    /**
     * Helper method to scale the art resource.
     * @param bitmap is the bitmap to be resized
     * @param newWidth is the new Width
     * @param newHeight is the new Height
     * @return scaled bitmap
     */
    public static Bitmap getResizedBitmap(Bitmap bitmap, float newWidth, float newHeight) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        float scaleWidth = newWidth / width;
        float scaleHeight = newHeight / height;

        Matrix matrix = new Matrix();
        matrix.postScale(scaleWidth, scaleHeight);

        Bitmap resizedBitmap = Bitmap.createBitmap(
                bitmap, 0, 0, width, height, matrix, false);
        return resizedBitmap;
    }

}
