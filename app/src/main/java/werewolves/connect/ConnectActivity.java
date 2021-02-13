package werewolves.connect;

import android.annotation.SuppressLint;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.io.IOException;
import java.util.Objects;

public class ConnectActivity extends AppCompatActivity{

    public static final int MAX_LOGIN_LENGTH = 8;

    private EditText nicknameField;
    private EditText gameIdField;
    private TextView infoLabel;
    private Button connectButton;
    private ProgressBar connectingBar;
    private ProgressBar waitingForGameBar;
    private boolean connected = false;

    @Override
    protected void onCreate( @Nullable Bundle savedInstanceState ){
        super.onCreate( savedInstanceState );
        getWindow().setFlags( WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN );
        setRequestedOrientation( ActivityInfo.SCREEN_ORIENTATION_PORTRAIT );
        Objects.requireNonNull( getSupportActionBar() ).hide();

        setContentView( R.layout.activity_fullscreen );
        nicknameField = findViewById( R.id.nicknameField );
        gameIdField = findViewById( R.id.gameIdField );
        infoLabel = findViewById( R.id.infoLabel );
        connectButton = findViewById( R.id.connectButton );
        connectingBar = findViewById( R.id.connectingBar );
        waitingForGameBar = findViewById( R.id.waitingForGameBar );

        connectButton.setOnClickListener( v -> connect() );

        nicknameField.addTextChangedListener( new TextWatcher(){
            @Override
            public void beforeTextChanged( CharSequence s, int start, int count, int after ){}

            @Override
            public void onTextChanged( CharSequence s, int start, int before, int count ){}

            @Override
            public void afterTextChanged( Editable s ){
                checkNickname();
            }
        } );
    }

    private long backPressedTime = 0;
    @SuppressLint( "ShowToast" )
    @Override
    public void onBackPressed(){
        if( backPressedTime + 3000 > System.currentTimeMillis() || !connected ){
            if( connected ){
                try{
                    Model.getSocket().close();
                } catch( IOException ignored ){}
            }
            super.onBackPressed();
            return;
        }
        else
            Toast.makeText( this, "Are you sure?\nPress again to exit.", Toast.LENGTH_SHORT ).show();
        backPressedTime = System.currentTimeMillis();
    }

    @SuppressLint( "SetTextI18n" )
    private boolean checkNickname(){
        String nickname = nicknameField.getText().toString();
        if( nickname.length() > MAX_LOGIN_LENGTH ){
            nicknameField.setError( "Nickname too long." );
            return false;
        }
        else if( nickname.length() <= 0 ){
            nicknameField.setError( "This field cannot be blank." );
            return false;
        }
        return true;
    }

    private boolean checkGameId(){
        if( gameIdField.getText().toString().length() <= 0 ){
            gameIdField.setError( "This field cannot be blank." );
            return false;
        }
        return true;
    }


    @SuppressLint( "SetTextI18n" )
    protected void connect(){
        String login = nicknameField.getText().toString();
        boolean fieldCheck = checkGameId() & checkNickname();
        if( !fieldCheck ){
            info( "Fields content is not valid." );
            return;
        }
        new Connect( login, gameIdField.getText().toString(), this );
    }

    public void info( String info ){
        infoLabel.setText( info );
    }

    @SuppressLint( "SetTextI18n" )
    public void connected( boolean connected ){
        connectingBar.setVisibility( View.INVISIBLE );
        if( connected ){
            connectButton.setText( "Joined" );
            waitingForGameBar.setVisibility( View.VISIBLE );
            this.connected = true;
        }
        else{
            connectButton.setText( "Join" );
            connectButton.setEnabled( true );
            nicknameField.setEnabled( true );
            gameIdField.setEnabled( true );
        }
    }

    @SuppressLint( "SetTextI18n" )
    public void connecting(){
        connectButton.setText( "Joining" );
        connectButton.setEnabled( false );
        nicknameField.setEnabled( false );
        gameIdField.setEnabled( false );
        connectingBar.setVisibility( View.VISIBLE );
    }

    public void started(){
        waitingForGameBar.setVisibility( View.INVISIBLE );
        infoLabel.setText( "" );
    }
}