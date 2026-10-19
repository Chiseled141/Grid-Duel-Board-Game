import java.awt.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.File;

/** Local composition preview using the real JavaFX score rail and placeholder card faces. */
class CardTablePreview {
 static Graphics2D g;
 static Color cream=new Color(242,225,189),gold=new Color(192,151,83);
 static void text(String s,int x,int y,int size,Color c){g.setFont(new Font("Serif",Font.PLAIN,size));g.setColor(new Color(0,0,0,180));g.drawString(s,x+1,y+2);g.setColor(c);g.drawString(s,x,y);}
 static void line(int x,int y,int w){g.setColor(new Color(192,151,83,100));g.drawLine(x,y,x+w,y);}
 static void card(int x,int y,int w,int h,String name,String kind,String value,boolean selected){
  g.setColor(new Color(0,0,0,100));g.fillRoundRect(x+5,y+8,w,h,7,7);
  g.setColor(new Color(45,39,31));g.fillRoundRect(x,y,w,h,7,7);
  if(selected){for(int halo=8;halo>0;halo--){g.setColor(new Color(238,191,102,5));g.setStroke(new BasicStroke(halo*2));g.drawRoundRect(x,y,w,h,7,7);}}
  g.setColor(selected?new Color(232,205,153):gold);g.setStroke(new BasicStroke(selected?2.5f:1));g.drawRoundRect(x,y,w,h,7,7);
  g.setColor(new Color(105,86,55));g.drawRect(x+6,y+6,w-12,h-12);
  text(value,x+11,y+28,23,cream);
  // Abstract heraldry keeps this layout independent of an unfinished card catalog.
  int cx=x+w/2,cy=y+h/2-7;
  g.setColor(new Color(142,112,63));g.fillPolygon(new int[]{cx-20,cx+20,cx+17,cx,cx-17},new int[]{cy-24,cy-24,cy+7,cy+25,cy+7},5);
  g.setColor(cream);g.setStroke(new BasicStroke(2));g.drawLine(cx,cy-16,cx,cy+12);g.drawLine(cx-10,cy-4,cx+10,cy-4);
  line(x+10,y+h-45,w-20);text(name,x+10,y+h-26,13,cream);text(kind,x+10,y+h-10,10,gold);
 }
 static void slot(int x,int y){g.setColor(new Color(227,201,152,45));g.setStroke(new BasicStroke(1,0,0,1,new float[]{4,6},0));g.drawRoundRect(x,y,100,143,5,5);g.setStroke(new BasicStroke(1));}
 /** Opens the interactive preview, or renders it with --render; run from the repository root. */
 public static void main(String[] args)throws Exception{
  BufferedImage im=new BufferedImage(1600,1000,BufferedImage.TYPE_INT_RGB);g=im.createGraphics();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
  BufferedImage bg=ImageIO.read(new File("src/main/resources/assets/match-room.png"));
  g.drawImage(bg,0,0,1600,1000,null);
  g.setPaint(new GradientPaint(0,0,new Color(0,0,0,165),0,420,new Color(0,0,0,0)));g.fillRect(0,0,1600,420);
  text("DECK DUEL",48,53,24,cream);text("THE CARD TABLE",48,78,11,gold);
  text("ROUND 1 OF 3",700,49,19,cream);text("I     ·     II     ·     III",721,78,14,gold);
  text("Settings     ·     Leave",1365,49,14,cream);
  for(int i=0;i<8;i++){
   int x=600+i*30;g.setColor(new Color(41,37,31));g.fillRoundRect(x,108,55,77,4,4);g.setColor(gold);g.drawRoundRect(x+4,112,47,69,3,3);
  }
  text("OPPONENT'S HAND  ·  8 CARDS",618,209,11,cream);

  for(int i=0;i<8;i++)slot(290+i*112,324);
  for(int i=0;i<8;i++)slot(290+i*112,561);
  for(int i=0;i<4;i++)card(290+i*112,324,100,143,i==3?"LANDMARK":"UNIT",i==3?"ONGOING EFFECT":"REGION · CLASS",i==3?"◇":"3",false);
  for(int i=0;i<3;i++)card(290+i*112,561,100,143,"UNIT","REGION · CLASS","3",true);
  g.setColor(new Color(212,177,112));g.setStroke(new BasicStroke(2));
  g.drawArc(340,552,225,35,0,180);g.drawArc(452,552,113,22,0,180);
  text("+9",406,555,19,cream);text("+9",507,550,19,cream);
  g.setStroke(new BasicStroke(1));
  text("+21",651,624,43,cream);text("3 points · 18 in bonds",653,651,14,gold);
  line(290,510,350);line(846,510,340);text("YOUR TURN",675,517,19,cream);
  text("OPPONENT'S ROW",290,308,11,cream);text("YOUR ROW",290,546,11,cream);
  for(int j=0;j<3;j++){int y=335+j*3;g.setColor(new Color(44,39,31));g.fillRoundRect(1310+j*3,y,91,129,5,5);g.setColor(gold);g.drawRoundRect(1314+j*3,y+4,83,121,4,4);}
  text("DRAW PILE",1321,488,12,cream);text("Cards remaining",1310,509,12,gold);
  g.setColor(new Color(225,202,156,60));g.drawRoundRect(1310,574,95,127,5,5);text("DISCARD",1321,728,12,cream);
  text("YOUR HAND",292,761,11,cream);text("YOUR TURN · Select a card",946,761,14,cream);
  for(int i=0;i<8;i++)card(291+i*112,785-(i==3?20:0),100,166,i<5?"UNIT":i==5?"LANDMARK":"SPELL",i<5?"REGION · CLASS":i==5?"ONGOING EFFECT":"ONE-TIME EFFECT",i<5?"3":i==5?"◇":"✦",i==3);
  text("Chat",48,955,16,cream);text("Layout preview · cards and values are placeholders",1128,977,12,cream);
  g.dispose();
  boolean render=args.length>0 && args[0].equals("--render");
  java.util.concurrent.CountDownLatch done=new java.util.concurrent.CountDownLatch(1);
  javafx.application.Platform.startup(() -> {
   var rail = new deckduel.client.ui.ScoreRail();
   rail.update(sample(36),true,0);
   if(render){
    new javafx.scene.Scene(new javafx.scene.Group(rail),252,620);rail.resize(252,620);
    rail.layout();
    var output=im.createGraphics();
    output.drawImage(javafx.embed.swing.SwingFXUtils.fromFXImage(rail.snapshot(transparent(),null),null),24,249,null);
    output.dispose();
    done.countDown();
    return;
   }
   var backdrop=new javafx.scene.image.ImageView(javafx.embed.swing.SwingFXUtils.toFXImage(im,null));
   backdrop.setFitWidth(1280);backdrop.setFitHeight(800);
   var replay=new javafx.scene.control.Button("Replay scoring: 15 → 36");
   var pending=new javafx.animation.PauseTransition(javafx.util.Duration.millis(450));
   pending.setOnFinished(e -> rail.update(sample(36),false,0));
   replay.setOnAction(e -> { rail.update(sample(15),true,0); pending.playFromStart(); });
   rail.relocate(19,199);rail.resize(202,496);replay.relocate(1008,56);
   var scene=new javafx.scene.Scene(new javafx.scene.layout.Pane(backdrop,rail,replay),1280,800);
   var stage=new javafx.stage.Stage();
   stage.setTitle("Deck Duel — score panel preview");
   stage.setScene(scene);
   stage.setOnHidden(e -> { pending.stop(); rail.stopFeedback(); javafx.application.Platform.exit(); });
   stage.show();
  });
  if(render){done.await();ImageIO.write(im,"png",new File("docs/design/card-table-preview.png"));javafx.application.Platform.exit();}
 }
 static javafx.scene.SnapshotParameters transparent(){
  var parameters=new javafx.scene.SnapshotParameters();parameters.setFill(javafx.scene.paint.Color.TRANSPARENT);return parameters;
 }
 static deckduel.client.ui.ScoreRail.Display sample(long value){
  return new deckduel.client.ui.ScoreRail.Display(value,18,value==15?6:9,value==15?9:27,0,value==15?8:7,1);
 }
}
