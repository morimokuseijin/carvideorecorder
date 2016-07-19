package io.github.sckzw.carvideorecorder;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.media.MediaRecorder.OnInfoListener;
import android.os.IBinder;
import android.os.Vibrator;
import android.support.v4.app.NotificationCompat;
import android.text.format.DateFormat;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;

import java.io.IOException;
import java.util.Calendar;

public class RecorderService extends Service implements OnInfoListener, SurfaceHolder.Callback {
    private static final int VIDEO_DURATION = 30 * 60 * 1000;
    private static final long VIDEO_FILESIZE = 1024 * 1024 * 1024;

    private SurfaceView mSurfaceView;
    private Camera mCamera;
    private MediaRecorder mMediaRecorder;
    private SurfaceHolder mSurfaceHolder;
    private boolean isRecording = false;

    public RecorderService() {
    }

    @Override
    public IBinder onBind( Intent intent ) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        // MediaRecorderのプレビュー用のSurfaceViewを作成する
        mSurfaceView = new SurfaceView( this );
        mSurfaceHolder = mSurfaceView.getHolder();
        mSurfaceHolder.addCallback( this );

        // SurfaceViewをシステムオーバーレイに登録する
        WindowManager windowManager = (WindowManager)getSystemService( WINDOW_SERVICE );
        WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams(
                1, 1,
                WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT );
        windowManager.addView( mSurfaceView, layoutParams );
    }

    @Override
    public int onStartCommand( Intent intent, int flags, int startId ) {
        super.onStartCommand( intent, flags, startId );

        if ( isRecording )
            mSurfaceView.setVisibility( View.VISIBLE );

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        // 録画停止する
        stopRecording();

        // SurfaceViewをシステムオーバーレイから削除する
        ( (WindowManager)getSystemService( WINDOW_SERVICE ) ).removeView( mSurfaceView );
        mSurfaceView = null;
    }

    /**
     * プレビュー用のSurfaceViewが作成された後に録画を開始する
     *
     * @param holder
     */
    public void surfaceCreated( SurfaceHolder holder ) {
        if ( !isRecording ) {
            // 録画中でなければ録画開始する
            startRecording();
        } else {
            // 録画中であれば録画再開する
            restartRecording();
        }

        // SurfaceViewは録画開始後は不要なため非表示にする
        mSurfaceView.setVisibility( View.INVISIBLE );
    }

    public void surfaceDestroyed( SurfaceHolder holder ) {
    }

    public void surfaceChanged( SurfaceHolder holder, int format, int width, int height ) {
    }

    /**
     * 録画を開始する
     */
    public void startRecording() {
        // 録画中であれば何もしない
        if ( isRecording )
            return;

        isRecording = true;

        // 通知をタッチした際にMainActivityを起動するためのIntentを作成する
        Intent intent = new Intent( this, MainActivity.class ).addFlags( Intent.FLAG_ACTIVITY_NEW_TASK );
        PendingIntent pendingIntent = PendingIntent.getActivity( this, 0, intent, 0 );

        // 通知を作成する
        Notification notification = new NotificationCompat.Builder( getApplicationContext() )
                .setContentIntent( pendingIntent )
                .setContentTitle( getString( R.string.app_name ) )
                .setContentText( getString( R.string.recording ) )
                .setTicker( getString( R.string.start_recording ) )
                .setSmallIcon( R.mipmap.ic_launcher )
                .setOngoing( true )
                .build();

        // Serviceをフォアグラウンド化して常時録画を維持する
        startForeground( 1, notification );

        // Cameraのフォーカスモードを無限遠に設定する
        mCamera = Camera.open();
        Camera.Parameters cameraParameters = mCamera.getParameters();
        cameraParameters.setFocusMode( Camera.Parameters.FOCUS_MODE_INFINITY );
        mCamera.setParameters( cameraParameters );
        mCamera.unlock();

        // MediaRecorderを設定する
        mMediaRecorder = new MediaRecorder();
        mMediaRecorder.setCamera( mCamera );

        // オーディオのノイズを抑制するため音声認識用のオーディオソースを使用する
        mMediaRecorder.setAudioSource( MediaRecorder.AudioSource.VOICE_RECOGNITION );
        mMediaRecorder.setVideoSource( MediaRecorder.VideoSource.CAMERA );

        // 高品質プロファイルを設定する
        mMediaRecorder.setProfile( CamcorderProfile.get( CamcorderProfile.QUALITY_HIGH ) );

        // 録画ファイルのパスを設定する(TODO: 外部ストレージのルートパスは端末ごとに修正する)
        mMediaRecorder.setOutputFile( "/sdcard/video/" + DateFormat.format( "yyyyMMdd'-'kkmmss", Calendar.getInstance() ) + ".mp4" );

        // 録画時間または録画ファイルサイズを制限する
        mMediaRecorder.setOnInfoListener( this );
        mMediaRecorder.setMaxDuration( VIDEO_DURATION );
        mMediaRecorder.setMaxFileSize( VIDEO_FILESIZE );

        // プレビュー用のSurfaceを設定する
        mMediaRecorder.setPreviewDisplay( mSurfaceHolder.getSurface() );

        // 録画を開始する
        try {
            mMediaRecorder.prepare();
            mMediaRecorder.start();
        } catch ( IOException ex ) {
            ex.printStackTrace();

            // 録画に失敗した場合はクリーンアップする

            // Serviceのフォアグラウンド化を解除する
            stopForeground( true );

            // MediaRecorderとCameraを解放する
            mMediaRecorder.release();
            mCamera.lock();
            mCamera.release();

            isRecording = false;

            // バイブレーションで通知する
            ( (Vibrator)getSystemService( VIBRATOR_SERVICE ) ).vibrate( 3000 );
        }
    }

    /**
     * 録画を停止する
     */
    public void stopRecording() {
        // 録画中でなければ何もしない
        if ( !isRecording )
            return;

        stopForeground( true );

        mMediaRecorder.stop();
        mMediaRecorder.release();

        mCamera.lock();
        mCamera.release();

        isRecording = false;
    }

    /**
     * 録画を再開する
     */
    public void restartRecording() {
        // 録画中でなければ何もしない
        if ( !isRecording )
            return;

        // 録画を停止する
        mMediaRecorder.stop();
        mMediaRecorder.reset();
        mCamera.lock();
        mCamera.release();

        // Cameraを再設定する
        mCamera = Camera.open();
        Camera.Parameters cameraParameters = mCamera.getParameters();
        cameraParameters.setFocusMode( Camera.Parameters.FOCUS_MODE_INFINITY );
        mCamera.setParameters( cameraParameters );
        mCamera.unlock();

        // MediaRecorderを再設定する
        mMediaRecorder.setCamera( mCamera );
        mMediaRecorder.setAudioSource( MediaRecorder.AudioSource.VOICE_RECOGNITION );
        mMediaRecorder.setVideoSource( MediaRecorder.VideoSource.CAMERA );
        mMediaRecorder.setProfile( CamcorderProfile.get( CamcorderProfile.QUALITY_HIGH ) );
        mMediaRecorder.setOutputFile( "/sdcard/video/" + DateFormat.format( "yyyyMMdd'-'kkmmss", Calendar.getInstance() ) + ".mp4" );
        // mMediaRecorder.setOnInfoListener( this );
        // mMediaRecorder.setMaxDuration( VIDEO_DURATION );
        // mMediaRecorder.setMaxFileSize( VIDEO_FILESIZE );
        mMediaRecorder.setPreviewDisplay( mSurfaceHolder.getSurface() );

        // 録画を開始する
        try {
            mMediaRecorder.prepare();
            mMediaRecorder.start();
        } catch ( IOException ex ) {
            ex.printStackTrace();

            stopForeground( true );

            mMediaRecorder.release();

            mCamera.lock();
            mCamera.release();

            isRecording = false;

            ( (Vibrator)getSystemService( VIBRATOR_SERVICE ) ).vibrate( 3000 );
        }
    }

    public void onInfo( MediaRecorder mr, int what, int extra ) {
        // 録画時間または録画ファイルサイズが制限に達した場合、SurfaceViewを再表示し録画を再開する
        if ( what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED || what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED ) {
            mSurfaceView.setVisibility( View.VISIBLE );
        }
    }
}
