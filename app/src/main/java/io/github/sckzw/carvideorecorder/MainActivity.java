package io.github.sckzw.carvideorecorder;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate( Bundle savedInstanceState ) {
        super.onCreate( savedInstanceState );

        setContentView( R.layout.activity_main );

        // 録画中の場合はボタンに停止のテキストを表示する
        if ( isRecording() ) {
            ( (Button)findViewById( R.id.buttonStartStop ) ).setText( getString( R.string.stop ) );
        }
    }

    public void onButtonClick( View v ) {
        Intent intent = new Intent( MainActivity.this, RecorderService.class );

        // 録画中であれば録画停止し、録画中でなければ録画開始する
        if ( isRecording() ) {
            ( (Button)findViewById( R.id.buttonStartStop ) ).setText( getString( R.string.start ) );

            stopService( intent );
        } else {
            ( (Button)findViewById( R.id.buttonStartStop ) ).setText( getString( R.string.stop ) );

            startService( intent.addFlags( Intent.FLAG_ACTIVITY_NEW_TASK ) );
        }
    }

    /**
     * 録画中かどうかを返す
     *
     * @return 録画中であればtrue
     */
    private boolean isRecording() {
        ActivityManager manager = (ActivityManager)getSystemService( Context.ACTIVITY_SERVICE );

        // RecorderServiceが起動中かどうかで録画中かどうかを判断する
        for ( ActivityManager.RunningServiceInfo info : manager.getRunningServices( Integer.MAX_VALUE ) ) {
            if ( info.service.getClassName().equals( RecorderService.class.getName() ) ) {
                return true;
            }
        }

        return false;
    }
}
