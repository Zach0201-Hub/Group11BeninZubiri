import javax.swing.*;

public class GameWindow extends JFrame {
    public static final int WIDTH  = 1000;
    public static final int HEIGHT = 900;  // taller to fit the long oval

    private GamePanel gamePanel;

    public GameWindow() {
        setTitle("APEX CIRCUIT – Time Trial");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(false);

        gamePanel = new GamePanel();
        add(gamePanel);

        pack();
        setLocationRelativeTo(null);
        gamePanel.startGame();
    }
}
