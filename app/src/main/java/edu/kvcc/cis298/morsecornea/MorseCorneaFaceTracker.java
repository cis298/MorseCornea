/*
 * Copyright (C) The Android Open Source Project
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
package edu.kvcc.cis298.morsecornea;

import android.graphics.PointF;
import android.util.Log;

import com.google.android.gms.vision.Tracker;
import com.google.android.gms.vision.face.Face;
import com.google.android.gms.vision.face.FaceDetector;
import com.google.android.gms.vision.face.Landmark;

import java.util.HashMap;
import java.util.Map;

import edu.kvcc.cis298.morsecornea.ui.camera.GraphicOverlay;

/**
 * Tracks the eye positions and state over time, managing an underlying graphic which renders googly
 * eyes over the source video.<p>
 *
 * To improve eye tracking performance, it also helps to keep track of the previous landmark
 * proportions relative to the detected face and to interpolate landmark positions for future
 * updates if the landmarks are missing.  This helps to compensate for intermediate frames where the
 * face was detected but one or both of the eyes were not detected.  Missing landmarks can happen
 * during quick movements due to camera image blurring.
 */
class MorseCorneaFaceTracker extends Tracker<Face> {
    private static final float EYE_CLOSED_THRESHOLD = 0.4f;

    private static final String TAG = "MorseCornea";

    private GraphicOverlay mOverlay;
    private GooglyEyesGraphic mEyesGraphic;

    // Record the previously seen proportions of the landmark locations relative to the bounding box
    // of the face.  These proportions can be used to approximate where the landmarks are within the
    // face bounding box if the eye landmark is missing in a future update.
    private Map<Integer, PointF> mPreviousProportions = new HashMap<>();

    // Similarly, keep track of the previous eye open state so that it can be reused for
    // intermediate frames which lack eye landmarks and corresponding eye state.
    private boolean mPreviousIsLeftOpen = true;
    private boolean mPreviousIsRightOpen = true;



    //Private properties I added

    private static final String DOT = "0";
    private static final String DASH = "1";

    private static final long DASH_DURATION = 1500;
    private static final long LETTER_SPACE_DURATION = 1500;
    private static final long WORD_SPACE_DURATION = 3500;

    private boolean mLetterAdded = false;
    private boolean mWordAdded = false;

    private long mPreviousTimestamp = System.currentTimeMillis();

    private String mCurrentLetterCode = "";
    private String mCurrentWord = "";

    public String getCurrentMessage() {
        return mCurrentMessage;
    }

    private String mCurrentMessage = "";

    private boolean mIsInCloseDuration = false;

    private HashMap<String, String> mCodes = new HashMap<>();

    private BlinkActivity mBlinkActivity;




    //==============================================================================================
    // Methods
    //==============================================================================================

    MorseCorneaFaceTracker(GraphicOverlay overlay, BlinkActivity blinkActivity) {
        mOverlay = overlay;
        mBlinkActivity = blinkActivity;

        mCodes.put("01", "A");
        mCodes.put("1000", "B");
        mCodes.put("1010", "C");
        mCodes.put("100", "D");
        mCodes.put("0", "E");
        mCodes.put("0010", "F");
        mCodes.put("110", "G");
        mCodes.put("0000", "H");
        mCodes.put("00", "I");
        mCodes.put("0111", "J");
        mCodes.put("101", "K");
        mCodes.put("0100", "L");
        mCodes.put("11", "M");
        mCodes.put("10", "N");
        mCodes.put("111", "O");
        mCodes.put("0110", "P");
        mCodes.put("1101", "Q");
        mCodes.put("010", "R");
        mCodes.put("000", "S");
        mCodes.put("1", "T");
        mCodes.put("001", "U");
        mCodes.put("0001", "V");
        mCodes.put("011", "W");
        mCodes.put("1001", "X");
        mCodes.put("1011", "Y");
        mCodes.put("1100", "Z");

        mCodes.put("01111", "1");
        mCodes.put("00111", "2");
        mCodes.put("00011", "3");
        mCodes.put("00001", "4");
        mCodes.put("00000", "5");
        mCodes.put("10000", "6");
        mCodes.put("11000", "7");
        mCodes.put("11100", "8");
        mCodes.put("11110", "9");
        mCodes.put("11111", "0");


    }

    /**
     * Resets the underlying googly eyes graphic and associated physics state.
     */
    @Override
    public void onNewItem(int id, Face face) {
        mEyesGraphic = new GooglyEyesGraphic(mOverlay);
    }

    /**
     * Updates the positions and state of eyes to the underlying graphic, according to the most
     * recent face detection results.  The graphic will render the eyes and simulate the motion of
     * the iris based upon these changes over time.
     */
    @Override
    public void onUpdate(FaceDetector.Detections<Face> detectionResults, Face face) {
        mOverlay.add(mEyesGraphic);

        updatePreviousProportions(face);

        PointF leftPosition = getLandmarkPosition(face, Landmark.LEFT_EYE);
        PointF rightPosition = getLandmarkPosition(face, Landmark.RIGHT_EYE);

        float leftOpenScore = face.getIsLeftEyeOpenProbability();
        boolean isLeftOpen;
        if (leftOpenScore == Face.UNCOMPUTED_PROBABILITY) {
            isLeftOpen = mPreviousIsLeftOpen;
        } else {
            isLeftOpen = (leftOpenScore > EYE_CLOSED_THRESHOLD);
            mPreviousIsLeftOpen = isLeftOpen;
        }

        float rightOpenScore = face.getIsRightEyeOpenProbability();
        boolean isRightOpen;
        if (rightOpenScore == Face.UNCOMPUTED_PROBABILITY) {
            isRightOpen = mPreviousIsRightOpen;
        } else {
            isRightOpen = (rightOpenScore > EYE_CLOSED_THRESHOLD);
            mPreviousIsRightOpen = isRightOpen;
        }

        mEyesGraphic.updateEyes(leftPosition, isLeftOpen, rightPosition, isRightOpen);

        boolean atLeastOneEyeIsClosed = !(isLeftOpen && isRightOpen);

        //If one of the eyes are closed
        if (atLeastOneEyeIsClosed) {
            Log.d(TAG, "At least one eye is closed");
            //If the tracker is currently in the open state and changing to the closed state right now
            if (isInOpenDuration()) {
                Log.d(TAG, "InOpenDuration transitioning to closed");
                this.transitionToCloseState();
            }
            //Set program to be in closed duration state
            mIsInCloseDuration = true;

         //Else, both eyes are open
        } else {
            Log.d(TAG, "Both eyes are open");
            //If the tracker is currently in the closed state and changing to the open state right now
            if (isInCloseDuration()) {
                Log.d(TAG, "InClosedDuration transitioning to open");
                this.transitionToOpenState();
            } else {
                Log.d(TAG, "InOpenDuration and checking to see if a letter needs to be finished");
                this.openStateCheck();
            }

            //Set program to be in open duration state
            mIsInCloseDuration = false;
        }
    }

    /**
     * Hide the graphic when the corresponding face was not detected.  This can happen for
     * intermediate frames temporarily (e.g., if the face was momentarily blocked from
     * view).
     */
    @Override
    public void onMissing(FaceDetector.Detections<Face> detectionResults) {
        mOverlay.remove(mEyesGraphic);
    }

    /**
     * Called when the face is assumed to be gone for good. Remove the googly eyes graphic from
     * the overlay.
     */
    @Override
    public void onDone() {
        mOverlay.remove(mEyesGraphic);
    }

    //==============================================================================================
    // Private
    //==============================================================================================

    private void updatePreviousProportions(Face face) {
        for (Landmark landmark : face.getLandmarks()) {
            PointF position = landmark.getPosition();
            float xProp = (position.x - face.getPosition().x) / face.getWidth();
            float yProp = (position.y - face.getPosition().y) / face.getHeight();
            mPreviousProportions.put(landmark.getType(), new PointF(xProp, yProp));
        }
    }

    /**
     * Finds a specific landmark position, or approximates the position based on past observations
     * if it is not present.
     */
    private PointF getLandmarkPosition(Face face, int landmarkId) {
        for (Landmark landmark : face.getLandmarks()) {
            if (landmark.getType() == landmarkId) {
                return landmark.getPosition();
            }
        }

        PointF prop = mPreviousProportions.get(landmarkId);
        if (prop == null) {
            return null;
        }

        float x = face.getPosition().x + (prop.x * face.getWidth());
        float y = face.getPosition().y + (prop.y * face.getHeight());
        return new PointF(x, y);
    }

    private boolean isInOpenDuration() {
        return !mIsInCloseDuration;
    }

    private boolean isInCloseDuration() {
        return mIsInCloseDuration;
    }

    private void openStateCheck() {
        long currentOpenDuration = System.currentTimeMillis() - mPreviousTimestamp;

        if (currentOpenDuration >= LETTER_SPACE_DURATION && !mLetterAdded) {
            this.addLetterToWord();
            mLetterAdded = true;
        }

        if (currentOpenDuration >= WORD_SPACE_DURATION && !mWordAdded) {
            this.addWordToMessage();
            mWordAdded = true;
        }
    }

    private void transitionToCloseState() {

        this.openStateCheck();

        mLetterAdded = false;
        mWordAdded = false;
    }

    private void transitionToOpenState() {

        //Calculate the current duration between when the eyes were first closed and now
        long currentClosedDuration = System.currentTimeMillis() - mPreviousTimestamp;

        if (currentClosedDuration > DASH_DURATION) {
            //Add dash to the current letter we are working on
            mCurrentLetterCode += DASH;
        } else {
            //Add dot to the current letter we are working on
            mCurrentLetterCode += DOT;
        }

        mLetterAdded = false;
        mWordAdded = false;

        mPreviousTimestamp = System.currentTimeMillis();
    }


    private void addLetterToWord() {
        String currentLetter = mCodes.get(mCurrentLetterCode);

        if (currentLetter != null) {
            mCurrentWord += currentLetter;
            Log.d(TAG, "Current Word Now: " + mCurrentWord);
        }

        mBlinkActivity.updateMessageEditText(mCurrentMessage + mCurrentWord);

        mCurrentLetterCode = "";
    }

    private void addWordToMessage() {
        mCurrentMessage += mCurrentWord + " ";
        Log.d(TAG, "Current Message Now: " + mCurrentMessage);

        mBlinkActivity.updateMessageEditText(mCurrentMessage);

        mCurrentWord = "";
    }

}