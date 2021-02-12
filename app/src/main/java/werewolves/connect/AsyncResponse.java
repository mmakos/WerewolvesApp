package werewolves.connect;

public interface AsyncResponse{
    void onConnected( boolean result );
    void onGameStarted();
}
