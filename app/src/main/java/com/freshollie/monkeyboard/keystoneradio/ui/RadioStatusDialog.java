/*
 * Created by Oliver Bell on 11/02/2017
 * Copyright (c) 2017. by Oliver bell <freshollie@gmail.com>
 *
 * Last modified 02/06/17 01:34
 */

package com.freshollie.monkeyboard.keystoneradio.ui;

import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.freshollie.monkeyboard.keystoneradio.R;
import com.freshollie.monkeyboard.keystoneradio.playback.RadioPlayerService;
import com.freshollie.monkeyboard.keystoneradio.radio.RadioDeviceListenerManager;
import com.freshollie.monkeyboard.keystoneradio.radio.RadioDevice;

/**
 * Dialog used to show progress of a DAB search or channel copy
 */

public class RadioStatusDialog extends DialogFragment {
    private final String TAG = getClass().getSimpleName();
    private RadioPlayerService playerService;
    private RadioDevice radio;

    public enum State {
        Connecting,
        Searching,
        Copying,
        Failed
    }

    private State currentState = State.Connecting;

    private ProgressBar progressBar;
    private TextView statusText;
    private TextView progressText;

    public void setPlayerService(RadioPlayerService service) {
        playerService = service;
        radio = playerService.getRadio();
    }

    public void setInitialState(State initialState) {
        currentState = initialState;
    };

    private void setState(State newState) {
        currentState = newState;
        onStateUpdated();
    }

    private void onStateUpdated() {
        if (currentState == State.Connecting) {
            statusText.setText(getString(R.string.dialog_dab_search_status_connecting));
            progressBar.setProgress(0);
            progressBar.setVisibility(View.VISIBLE);
            progressText.setText("");
            if (radio.isConnected()) {
                setState(State.Searching);
            } else {
                playerService.openConnection();
            }

        } else if (currentState == State.Searching) {
            statusText.setText(getString(R.string.dialog_dab_search_status_searching));
            progressBar.setVisibility(View.VISIBLE);
            progressBar.setProgress(0);
            progressBar.setMax(RadioDevice.Values.MAX_CHANNEL_BAND);
            progressText.setText(getString(R.string.dialog_dab_search_found_channels_progress, 0));
            if (radio.isConnected()) {
                playerService.startDabChannelSearchTask();
            }

        } else if (currentState == State.Copying) {
            statusText.setText(getString(R.string.dialog_dab_search_status_copying));
            progressBar.setVisibility(View.VISIBLE);
            progressBar.setProgress(0);
            progressText.setText(getString(R.string.dialog_dab_search_copying_channels_progress, 0));
        }
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        // Get the layout inflater
        LayoutInflater inflater = getActivity().getLayoutInflater();

        // Inflate and set the layout for the dialog
        // Pass null as the parent view because its going in the dialog layout
        View view = inflater.inflate(R.layout.search_dialog_content, null);

        progressBar = (ProgressBar) view.findViewById(R.id.search_dialog_progress_bar);
        progressText = (TextView) view.findViewById(R.id.search_dialog_progress_text);
        statusText = (TextView) view.findViewById(R.id.search_dialog_status_text);

        builder.setView(view);

        builder.setCancelable(false);
        builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                playerService.handleStopSearch();

                // Check if this dialog is being run in the player activity
                if (getActivity() != null &&
                        getActivity().getClass().getSimpleName()
                                .equals(PlayerActivity.class.getSimpleName())) {

                    // Switch back to FM mode if possible
                    SharedPreferences sharedPreferences =
                            PreferenceManager
                                    .getDefaultSharedPreferences(getActivity());
                    if (sharedPreferences
                            .getBoolean(
                                    getString(R.string.pref_fm_mode_enabled_key),
                                    true
                            )) {
                        playerService
                                .handleSetRadioMode(
                                        RadioDevice.Values.STREAM_MODE_FM
                                );
                    }
                }
                dismiss();
            }
        });

        return builder.create();
    }

    @Override
    public void onStart() {
        super.onStart();    //super.onStart() is where dialog.show()
        // is actually called on the underlying dialog, so we have to do it after this point

        if (playerService == null) {
            throw new RuntimeException("PlayerService needs to be given");
        }
        getDialog().setCanceledOnTouchOutside(false);

        playerService.registerCallback(playerCallback);

        radio.getListenerManager()
                .registerConnectionStateChangedListener(connectionStateChangeListener);
        onStateUpdated();

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        playerService.unregisterCallback(playerCallback);
        radio.getListenerManager()
                .unregisterConnectionStateChangedListener(connectionStateChangeListener);
        if (radio.isConnected()) {
            playerService.handleStopSearch();
        }
    }

    private RadioPlayerService.PlayerCallback playerCallback = new RadioPlayerService.PlayerCallback() {
        @Override
        public void onNoStoredStations() {
            if (currentState != State.Connecting) {
                statusText.setText(getString(R.string.dialog_dab_search_status_failed));
                progressBar.setVisibility(View.INVISIBLE);
                progressText.setText(getString(R.string.dialog_dab_search_failed_no_channels_found));
                new AlertDialog.Builder(getActivity())
                        .setMessage(getString(R.string.dialog_dab_search_try_again_message))
                        .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                setState(State.Connecting);
                            }
                        })
                        .setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                // Check if this dialog is being run in the player activity
                                if (getActivity() != null &&
                                        getActivity().getClass().getSimpleName()
                                                .equals("PlayerActivity")) {

                                    // Switch back to FM mode if possible
                                    SharedPreferences sharedPreferences =
                                            PreferenceManager
                                                    .getDefaultSharedPreferences(getActivity());
                                    if (sharedPreferences
                                            .getBoolean(
                                                    getString(R.string.pref_fm_mode_enabled_key),
                                                    true
                                            )) {
                                        playerService
                                                .handleSetRadioMode(
                                                        RadioDevice.Values.STREAM_MODE_FM
                                                );
                                    }
                                }
                                dismiss();

                            }
                        })
                        .show();
            } else {
                setState(State.Searching);
            }
        }

        @Override
        public void onDeviceAttachTimeout() {
            statusText.setText(getString(R.string.dialog_dab_search_status_failed));
            progressBar.setVisibility(View.INVISIBLE);
            progressText.setText(getString(R.string.dialog_dab_search_failed_timed_out));

            new AlertDialog.Builder(getActivity())
                    .setMessage(getString(R.string.device_connection_timed_out_try_again))
                    .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            playerService.openConnection();
                            setState(State.Connecting);
                        }
                    })
                    .setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            dismiss();
                        }
                    })
                    .show();
        }

        @Override
        public void onSearchStart() {
            if (currentState != State.Searching) {
                setState(State.Searching);
            }
        }

        @Override
        public void onSearchProgressUpdate(int numChannels, int progress) {
            if (currentState != State.Searching) {
                setState(State.Searching);
            }
            progressText.setText(
                    getString(R.string.dialog_dab_search_found_channels_progress,
                            numChannels)
            );

            progressBar.setProgress(progress);
        }

        @Override
        public void onSearchComplete(int numChannels) {

        }

        @Override
        public void onStationListCopyStart() {
            Log.v(TAG, "list copy start");
            if (currentState != State.Copying) {
                setState(State.Copying);
            }
        }

        @Override
        public void onStationListCopyProgressUpdate(int progress, int max) {
            if (currentState != State.Copying) {
                setState(State.Copying);
            }

            progressText.setText(
                    getString(R.string.dialog_dab_search_copying_channels_progress,
                            progress)
            );

            if (progressBar.getMax() != max) {
                progressBar.setMax(max);
            }

            progressBar.setProgress(progress);
        }

        @Override
        public void onStationListCopyComplete() {
            dismiss();
        }

        @Override
        public void onDismissed() {
            dismiss();
        }

        @Override
        public void onPlayerVolumeChanged(int newVolume) {

        }

        @Override
        public void onRadioModeChanged(int radioMode) {

        }
    };

    private RadioDeviceListenerManager.ConnectionStateChangeListener connectionStateChangeListener =
            new RadioDeviceListenerManager.ConnectionStateChangeListener() {
        @Override
        public void onStart() {
            setState(State.Searching);
            onStateUpdated();
        }

        @Override
        public void onFail() {
            dismiss();
        }

                @Override
        public void onStop() {
            dismiss();
        }
    };
}
