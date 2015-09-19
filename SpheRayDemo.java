import javax.swing.*;
import javax.swing.event.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import ptolemy.plot.*;
import edu.washington.apl.aganse.ptolemyUpdates.plot.*;
import edu.washington.apl.aganse.dataTools.*;
import java.net.URL;
import java.lang.Math;


//on applet tags themselves:
//http://java.sun.com/applets/
//on getting data from applet tags:
//http://java.sun.com/docs/books/tutorial/applet/appletsonly/getParam.html
//on the action architecture:
//http://java.sun.com/products/jfc/tsc/articles/actions/index.html

public class SpheRayDemo extends JApplet {

    private int MAXRUNS=200;
    private Action clearAction, propertiesAction, runAction, stopAction;
    private Action helpAction, aboutAction, showPlotsAction;
    private DataSeries VZData = new DataSeries();
    private DataSeries RPhiData[] = new DataSeries[MAXRUNS];
    private JPanel contentPanel;
    private PlotMatrix plotMatrix;
    private VZPanel vzpanel;
    private PolarPanel polarpanel;
    private JButton btnRun;
    private JTextField srcDepthTxt = new JTextField("0",2);
    private JTextField maxItersTxt = new JTextField("2000",5);
    private JTextField timeIncrTxt = new JTextField("20",3);
    private JTextField startAngleTxt = new JTextField("90",3);
    private JTextField endAngleTxt = new JTextField("0",3);
    private JTextField angleIncrTxt = new JTextField("10",2);
    private CalcThread calcThread = null;
    private boolean stopCalc = false, alreadyRunning=false;
    private String plotMode;
    // defining these globally simply so I can put them all up here - I know, bad form:
    private int width=750, height=350, VZwidth=200;
    private double surfaceRadius=7.0e5, maxVel=600.0;


    public void init() {

        //Get width, height and plotmode params from HTML APPLET or OBJECT call, or use defaults
        //String widthString = getParameter("width");
        //String heightString = getParameter("height");
        //if(widthString!=null) {
        //    width = Integer.parseInt(widthString);
        //}
        //if(heightString!=null) {
        //    height = Integer.parseInt(heightString); 
        //}

        //Put correct plots into plots panel depending on raydemo args
        plotMatrix=new PlotMatrix();
        plotMatrix.build();

        //Hook up actions to toolbar buttons:
        JToolBar toolBar = new JToolBar();
        toolBar.setBorder(BorderFactory.createEtchedBorder());
        createActionComponents(toolBar);

        //Lay out toolbar and plots panel in main app window:
        contentPanel = new JPanel();
        contentPanel.setLayout(new BorderLayout());
        contentPanel.setVisible(true);
        contentPanel.add(toolBar, BorderLayout.NORTH);
        contentPanel.add(plotMatrix, BorderLayout.CENTER);
        getContentPane().add(contentPanel);

    }

    private class PlotMatrix extends Box {
        public PlotMatrix() {
            super(BoxLayout.X_AXIS);
            //if(width < 850) {
            //    surfaceRadius=25;
            //} else if(width>=850 && width<1050) surfaceRadius=30;
            //else surfaceRadius=35;
            vzpanel = new VZPanel();
            polarpanel = new PolarPanel();
        }
        public void build() {
            Box box0 = new Box(BoxLayout.X_AXIS);
            JPanel p1 = new JPanel();
            vzpanel.setVisible(true);
            vzpanel.setPreferredSize(new Dimension(VZwidth,height));
            polarpanel.setPreferredSize(new Dimension(width-VZwidth,height));  // ie make square
            polarpanel.setVisible(true);
            //p1.setLayout(new GridLayout(2,1));
            //p1.add(vzpanel);
            //box0.add(p1);
            box0.add(vzpanel);
            box0.add(polarpanel);
            box0.setPreferredSize(new Dimension(width,height));
            add(box0);
        }
    }
    

    
    private class PolarPanel extends JPanel {
        private MenuPlot myplot;
        private String pointsOnOff="on";
        private JPopupMenu plotMenu;
        private int datasetnum;
        private DataSeries data;
        private DataSeries dataMulti[];
        private double xmin, ymin, xmax, ymax;
        private double surfCirclePtsX[] = new double[360];
        private double surfCirclePtsY[] = new double[360];
        public PolarPanel() {
            makePanel("Raypaths in Spherical Cross-Section","","",RPhiData,-surfaceRadius,+surfaceRadius,
                      -surfaceRadius,+surfaceRadius);  // multiple-dataseries-in-an-array version
            togglePlotPoints();
            getPlotHandle().setGrid(false);
            // creating the SurfaceCircle points just this once:
            for(int i=0;i<360;i++) {
                surfCirclePtsX[i]=surfaceRadius*cosd(i);
                surfCirclePtsY[i]=surfaceRadius*sind(i);
            }
            addSurfaceCircle();
        }
        /** single-dataseries version */
        public void makePanel(String title, String xlabel, String ylabel, int datasetnum,
                         DataSeries data, double xmin, double xmax, double ymin,
                         double ymax) {
            this.xmin=xmin; this.xmax=xmax; this.ymin=ymin; this.ymax=ymax;
            this.data=data;
            this.datasetnum=datasetnum;
            init(title, xlabel, ylabel);
            plotMenu = new JPopupMenu();
            addPlotMenuItems(plotMenu);
        }
        /** multiple-dataseries-in-an-array version */
        public void makePanel(String title, String xlabel, String ylabel,
                         DataSeries data[], double xmin, double xmax, double ymin,
                         double ymax) {
            this.xmin=xmin; this.xmax=xmax; this.ymin=ymin; this.ymax=ymax;
            this.dataMulti=data;
            init(title, xlabel, ylabel);
            plotMenu = new JPopupMenu();
            addPlotMenuItems(plotMenu);
        }
        public void addSurfaceCircle() {
            getPlotHandle().setNextDataColor(Color.black);
            getPlotHandle().addPoints(0, surfCirclePtsX, surfCirclePtsY, true);
        }
        public void fill() {myplot.fillPlot();}
        public void init(String title, String xlabel, String ylabel) {
            myplot = new MenuPlot();
            super.setLayout(new BorderLayout());
            super.add(myplot,BorderLayout.CENTER);
            myplot.setVisible(true);
            myplot.setButtons(false);
            myplot.setTitle(title);
            myplot.setXLabel(xlabel);
            myplot.setYLabel(ylabel);
            myplot.setMarksStyle("dots");
            resetXYRanges();
        }
        public void clear() {
            for(int i=0; i<MAXRUNS; i++) {
                myplot.clear(i);
            }
        }
        public void update() {
            myplot.clear(datasetnum);
            addSurfaceCircle();
            myplot.addPoints(datasetnum, data.xToArray(), data.yToArray(), true);
        }
        public void updateMulti() {
            double startAngle = Double.parseDouble(startAngleTxt.getText());
            double endAngle = Double.parseDouble(endAngleTxt.getText());
            double angleIncr = Double.parseDouble(angleIncrTxt.getText());
            int numrays = (int)((startAngle-endAngle)/angleIncr);
            myplot.clear(0);
            addSurfaceCircle();
            //System.out.println("updateMulti: stop="+stopCalc+", "+", "+dataMulti[0].getX(dataMulti[0].getNumPts()-1)+", "+dataMulti[0].getY(dataMulti[0].getNumPts()-1));
            for(int i=1; i<=numrays && stopCalc==false; i++) {
                myplot.clear(i);
                Color tmp = new Color(Color.HSBtoRGB(
                   (float)i / (float)(numrays) ,1,1) );
                myplot.setNextDataColor(tmp);
                // i is the dataseries number starting at 1, but dataMulti
                // is an array starting at zero, hence the i-1...
                myplot.addPoints(i, dataMulti[i-1].xToArray(),
                                    dataMulti[i-1].yToArray(), true);
            }
        }
        public void resetXYRanges() {
            myplot.setXRange(xmin,xmax);
            myplot.setYRange(ymin,ymax);
        }
        public XPlot getPlotHandle() { return myplot; }
        public class MenuPlot extends XPlot {
            public MenuPlot() {
            }
            /** catch mouse click and if location is valid add data point */
            public void mousePressed(MouseEvent e) {
                    if (e.isPopupTrigger() || e.getModifiers()==MouseEvent.BUTTON2_MASK
                    || e.getModifiers()==MouseEvent.BUTTON3_MASK) {
                    plotMenu.show(e.getComponent(), e.getX(), e.getY());
                }
            }
        }
        public void addPlotMenuItems(JPopupMenu plotMenu) {
            JMenuItem menuItem0 = new JMenuItem("Orig Data Range");
            menuItem0.setMnemonic(KeyEvent.VK_O);
            menuItem0.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        resetXYRanges();
                        repaint();
                    }
                });
            plotMenu.add(menuItem0);
            JMenuItem menuItem1 = new JMenuItem("Points On/Off");
            menuItem1.setMnemonic(KeyEvent.VK_C);
            menuItem1.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        togglePlotPoints();
                    }
                });
            plotMenu.add(menuItem1);
        }
        public void togglePlotPoints() {
            if(pointsOnOff.equals("on")) {
                myplot.setMarksStyle("none");
                pointsOnOff="off";
            }
            else if(pointsOnOff.equals("off")) {
                myplot.setMarksStyle("dots");
                pointsOnOff="on";
            }           
            repaint();
        }
    }


    private class VZPanel extends JPanel {
        private EditPlot myplot;
        private String pointsOnOff="on";
        private JPopupMenu _velMenu;
        private double xmin, ymin, xmax, ymax;
        public VZPanel() {
            _velMenu = new JPopupMenu();
            addVelMenuItems(_velMenu);
            myplot = new EditPlot();
            super.setLayout(new BorderLayout());
            super.add(myplot,BorderLayout.CENTER);
            //add(myplot);
            myplot.setVisible(true);
            myplot.setButtons(false);
            //myplot.clear(true);
            myplot.setTitle("WaveVel vs Radius");
            myplot.setXLabel("vel (km/s)");
            myplot.setYLabel("radius (km)");
            myplot.setMarksStyle("dots");
            xmin=0.0; xmax=maxVel;
            ymin=0.0; ymax=surfaceRadius; //wave vel V in km/s
            resetXYRanges(); //radius Z in km
        }
        public void resetXYRanges() {
            myplot.setXRange(xmin,xmax);
            myplot.setYRange(ymin,ymax);
        }
        public void update() {
            myplot.clear(0);
            myplot.addPoints(0, VZData.yToArray(), VZData.xToArray(), true);
        }
        public class EditPlot extends XPlot {
            public EditPlot() {
            }
            /** catch mouse click and if location is valid add data point */
            public void mousePressed(MouseEvent e) {
                if(e.getModifiers()==MouseEvent.BUTTON1_MASK) {
                    e.consume();
                    int tmpx=e.getX();
                    int tmpy=e.getY();
                    if(inPlotRegion(tmpx,tmpy) && getDataY(tmpy)<surfaceRadius*1.02 
                             && getDataY(tmpy)>surfaceRadius*-0.02) {
                        // the following is to make it easier for the user to enter pts @ z=surfaceRadius & z=0:
                        double datay;
                        if(getDataY(tmpy)<surfaceRadius*1.02 && getDataY(tmpy)>surfaceRadius*0.98) datay=surfaceRadius;
                        else if(getDataY(tmpy)<surfaceRadius*0.02 && getDataY(tmpy)>surfaceRadius*-0.02) datay=0;
                        else datay=getDataY(tmpy);
                        // note swapping x & y because want z-value (radius) on y axis
                        if(datay!=VZData.getMinX() && datay!=VZData.getMaxX())
                            VZData.add(datay,getDataX(tmpx));
                        VZData.sort();
                        vzpanel.update();
                    }
                }
                if (e.isPopupTrigger() || e.getModifiers()==MouseEvent.BUTTON2_MASK
                    || e.getModifiers()==MouseEvent.BUTTON3_MASK) {
                    _velMenu.show(e.getComponent(), e.getX(), e.getY());
                }
            }
        }
        public void addVelMenuItems(JPopupMenu velMenu) {
            JMenuItem menuItem0 = new JMenuItem("Orig Data Range");
            menuItem0.setMnemonic(KeyEvent.VK_O);
            menuItem0.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        resetXYRanges();
                        repaint();
                    }
                });
            velMenu.add(menuItem0);
            JMenuItem menuItem1 = new JMenuItem("Points On/Off");
            menuItem1.setMnemonic(KeyEvent.VK_P);
            menuItem1.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        if(pointsOnOff.equals("on")) {
                            myplot.setMarksStyle("none");
                            pointsOnOff="off";
                        }
                        else if(pointsOnOff.equals("off")) {
                            myplot.setMarksStyle("dots");
                            pointsOnOff="on";
                        }
                        repaint();
                    }
                });
            velMenu.add(menuItem1);
            velMenu.addSeparator();
            JMenuItem menuItem10 = new JMenuItem("Clear");
            menuItem10.setMnemonic(KeyEvent.VK_C);
            menuItem10.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        VZData.clear();
                        vzpanel.update();
                    }
                });
            velMenu.add(menuItem10);
            //velMenu.add(JPopupMenu.Separator);   // J2 v1.4 only?
            JMenuItem menuItem11 = new JMenuItem("Isovelocity");
            menuItem11.setMnemonic(KeyEvent.VK_S);
            menuItem11.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        VZData.clear();
                        VZData.add(surfaceRadius,maxVel/2.0);
                        VZData.add(0.0,maxVel/2.0);
                        vzpanel.update();
                    }
                });
            velMenu.add(menuItem11);
            JMenuItem menuItem12 = new JMenuItem("Const. Increasing");
            menuItem12.setMnemonic(KeyEvent.VK_I);
            menuItem12.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        VZData.clear();
                        VZData.add(surfaceRadius,maxVel*1.0/6.0);
                        VZData.add(0.0,maxVel*5.0/6.0);
                        vzpanel.update();
                    }
                });
            velMenu.add(menuItem12);
            JMenuItem menuItem13 = new JMenuItem("Const. Decreasing");
            menuItem13.setMnemonic(KeyEvent.VK_D);
            menuItem13.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        VZData.clear();
                        VZData.add(surfaceRadius,maxVel*5.0/6.0);
                        VZData.add(0.0,maxVel*1.0/6.0);
                        vzpanel.update();
                    }
                });
            velMenu.add(menuItem13);
            /**
            JMenuItem menuItem14 = new JMenuItem("Rapid Increase");
            menuItem14.setMnemonic(KeyEvent.VK_R);
            menuItem14.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        VZData.clear();
                        VZData.add(surfaceRadius,maxVel);
                        VZData.add(surfaceRadius*0.28,maxVel*0.62);  // arbitrary vel
                        VZData.add(surfaceRadius/5.0,maxVel*0.38);  // arbitrary vel
                        VZData.add(0.0,maxVel*1.0/3.0);  // arbitrary vel
                        vzpanel.update();
                    }
                });
            velMenu.add(menuItem14); */
            JMenuItem menuItem15 = new JMenuItem("B-P 2000 Model");
            menuItem15.setMnemonic(KeyEvent.VK_B);
            menuItem15.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        VZData.clear();
                        /* profile units are radius in km, sound speeds in km/s */
                        VZData.add(0.0,505.491);  // not in model
                        VZData.add(6.50453E-03*surfaceRadius,505.491);
                        VZData.add(1.09714E-02*surfaceRadius,505.713);
                        VZData.add(2.03647E-02*surfaceRadius,506.499);
                        VZData.add(3.82150E-02*surfaceRadius,508.689);
                        VZData.add(6.89000E-02*surfaceRadius,511.217);
                        VZData.add(9.50097E-02*surfaceRadius,508.839);
                        VZData.add(0.121429*surfaceRadius,500.954);
                        VZData.add(0.156715*surfaceRadius,483.143);
                        VZData.add(0.222106*surfaceRadius,439.758);
                        VZData.add(0.288752*surfaceRadius,396.471);
                        VZData.add(0.354040*surfaceRadius,360.650);
                        VZData.add(0.421087*surfaceRadius,329.742);
                        VZData.add(0.491539*surfaceRadius,302.089);
                        VZData.add(0.566053*surfaceRadius,276.286);
                        VZData.add(0.643953*surfaceRadius,250.350);
                        VZData.add(0.701386*surfaceRadius,228.873);
                        VZData.add(0.720331*surfaceRadius,219.636);
                        VZData.add(0.760497*surfaceRadius,197.813);
                        VZData.add(0.820513*surfaceRadius,164.590);
                        VZData.add(0.867591*surfaceRadius,136.969);
                        VZData.add(0.903324*surfaceRadius,113.922);
                        VZData.add(0.929764*surfaceRadius,94.7171);
                        VZData.add(0.948961*surfaceRadius,78.7052);                      
                        VZData.add(0.948961*surfaceRadius,78.7052);
                        VZData.add(surfaceRadius,78.7052);  // not in model
                        vzpanel.update();
                    }
                });
            velMenu.add(menuItem15);
        }
    }
    
    
    /** Set up Actions and connect them to toolbar buttons */
    private void createActionComponents(JToolBar toolBar) {
        
        JButton button = null;       // gets reused as tmp placeholder
        
        //toolBar.add(new JToolBar.Separator());
        
        //Clear Action:
        URL url = this.getClass().getResource("images/New24.gif");
        clearAction = 
            new AbstractAction("Clear", new ImageIcon(url)) {
                public void actionPerformed(ActionEvent e) {
                    clearData();
                    VZData.clear(); vzpanel.update();
                }
            };
        button = toolBar.add(clearAction);
        button.setSize(18,18);
        button.setText(""); //an icon-only button
        button.setToolTipText("Clear Data");
        
        //Properties Action:
        url = this.getClass().getResource("images/Edit24.gif");
        propertiesAction = 
            new AbstractAction("Properties", new ImageIcon(url)) {
                public void actionPerformed(ActionEvent e) {
                    openPropertiesDialog();
                }
            };
        button = toolBar.add(propertiesAction);
        button.setSize(18,18);
        button.setText(""); //an icon-only button
        button.setToolTipText("Edit Run Properties");
        
        //Run Action:
        url = this.getClass().getResource("images/Refresh24.gif");
        runAction = 
            new AbstractAction("Run", new ImageIcon(url)) {
                public void actionPerformed(ActionEvent e) {
                    runCalc();
                }
            };
        button = toolBar.add(runAction);
        button.setSize(18,18);
        button.setText(""); //an icon-only button
        button.setToolTipText("Run Calculation");
        
        //Stop Action:
        url = this.getClass().getResource("images/Stop24.gif");
        stopAction = 
            new AbstractAction("Stop", new ImageIcon(url)) {
                public void actionPerformed(ActionEvent e) {
                    runCalc();
                }
            };
        button = toolBar.add(stopAction);
        button.setSize(18,18);
        button.setText(""); //an icon-only button
        button.setToolTipText("Stop Calculation Before Finished");
        stopAction.setEnabled(false);


        toolBar.add(Box.createHorizontalGlue());


        //Help Action:
        url = this.getClass().getResource("images/Help24.gif");
        helpAction =
            new AbstractAction("Help",
                               new ImageIcon(url)) {
                public void actionPerformed(ActionEvent e) {
                    openHelpDialog();
                }
            };
        button = toolBar.add(helpAction);
        button.setSize(18,18);
        button.setText(""); //an icon-only button
        button.setToolTipText("Help/Instructions");

        //About Action:
        url = this.getClass().getResource("images/About24.gif");
        aboutAction =
            new AbstractAction("About",
                               new ImageIcon(url)) {
                public void actionPerformed(ActionEvent e) {
                    openAboutDialog();
                }
            };
        button = toolBar.add(aboutAction);
        button.setSize(18,18);
        button.setText(""); //an icon-only button
        button.setToolTipText("About this applet");

        //toolBar.add(new JToolBar.Separator());



    }

    private void clearData(){
        // clear out plots from any previous runs
        for(int r=0; r<MAXRUNS; r++) {
            if(RPhiData[r]!=null) {
                RPhiData[r].clear();
            }
        }
        polarpanel.clear();
    }

    private void openPropertiesDialog(){

        final JDialog propertiesDialog = new JDialog();
        propertiesDialog.setTitle("SpheRayDemo Run Properties");
        Box propertiesPanel = new Box(BoxLayout.Y_AXIS);
        JPanel p2 = new JPanel();
        p2.add(new JLabel("Source Depth :"));
        p2.add(srcDepthTxt);
        //JPanel p1 = new JPanel();
        JPanel p6 = new JPanel();
        p6.setLayout(new GridLayout(2,2));
        p6.add(new JLabel("Max # Iters :"));
        p6.add(new JLabel("Time Incr (s):"));
        JPanel p7 = new JPanel();
        p7.add(maxItersTxt); 
        JPanel p8 = new JPanel();
        p8.add(timeIncrTxt);
        p6.add(p7); p6.add(p8);
        JPanel p3 = new JPanel();
        p3.add(new JLabel("Angles (wrt vertical/down) :"));
        p3.add(new JLabel("(note larger angle first)"));
        JPanel p4 = new JPanel();
        p4.add(new JLabel("1st:"));
        p4.add(startAngleTxt);
        p4.add(new JLabel("Last:"));
        p4.add(endAngleTxt);
        p4.add(new JLabel("Incr:"));
        p4.add(angleIncrTxt);
        JPanel p5 = new JPanel();
        p5.setLayout(new GridLayout(2,1));
        p5.add(p3); p5.add(p4);
        JPanel p10 = new JPanel();
        Action okAction = new AbstractAction("Ok") {
            public void actionPerformed(ActionEvent e) {
                propertiesDialog.dispose();
            }
        };
        p10.add(new JButton(okAction));
        propertiesPanel.add(p2);
        //propertiesPanel.add(p1);
        propertiesPanel.add(p6);
        propertiesPanel.add(p5);
        propertiesPanel.add(p10);
        propertiesPanel.setBorder(new 
            TitledBorder(BorderFactory.createEtchedBorder(), "SpheRayDemo Properties"));
        propertiesDialog.getContentPane().add(propertiesPanel);
        propertiesDialog.setSize(270,340);
        propertiesDialog.setVisible(true);
        propertiesDialog.addWindowListener( new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
            }
        });             
    }



    private void runCalc(){
        if(alreadyRunning) {
            alreadyRunning = false;
            stopCalc = true;
            stopAction.setEnabled(false);
            runAction.setEnabled(true);
        }
        else {
            clearData();
            VZData.sort();
            if(VZData.isEmpty()) {
                JOptionPane.showMessageDialog(contentPanel,
                    "You must enter some velocity data before the processing can run.",
                    "Error", JOptionPane.ERROR_MESSAGE);
            }
            else if(VZData.getNumPts()==1) {
                JOptionPane.showMessageDialog(contentPanel,
                    "You must enter more than one velocity point.",
                    "Error", JOptionPane.ERROR_MESSAGE);
            }
            else if( ((DataSeries.Point)(VZData.lastElement())).getX() < surfaceRadius) {
                JOptionPane.showMessageDialog(contentPanel,
                    "Your velocity profile must have an outter-radius entry to run.",
                    "Error", JOptionPane.ERROR_MESSAGE);
            }
            else {
                alreadyRunning = true;
                stopCalc = false;
                stopAction.setEnabled(true);
                runAction.setEnabled(false);
                calcThread = new CalcThread();
            }
        }
    }
    private void openHelpDialog(){
        JOptionPane.showMessageDialog(contentPanel,
            "Brief help entry for \"SpheRayDemo\" geophysical raytrace applet:\n\n" +
            "1.) Right-clicking on the backgrounds of the plots brings up menu for those plots.\n\n" +
            "2.) Hovering over a toolbar button will bring up a description of button.\n\n" +
            "3.) You can click & drag to zoom in on plots, and return to orig bounds via menus.\n\n" +
            "4.) \"Quick-start\" steps to use:\n" +
            "   - right-click on radius-vel plot and choose a premade profile type.\n" +
            "   - optionally left-click additional points into the radius-vel plot.\n" +
            "   - click the Run (swirling arrows) toolbar button to calculate/display rays.\n" +
            "   - optionally click the Plots toolbar button to show TX,PX,TP plots.\n" +
            "   - click the Clear toolbar button to start again.\n" +
            "   - click the Edit Properties toolbar button to tailor run parameters.\n" +
            "\n",
            "Help", JOptionPane.QUESTION_MESSAGE);
    }
    private void openAboutDialog(){
        JOptionPane.showMessageDialog(contentPanel,
            "\"SpheRayDemo\" geophysical raytrace applet\n" +
            "                   version 1.00\n" +
            "     by Andy Ganse, APL-UW, 2009\n" +
            "      aganse@apl.washington.edu",
            "About SpheRayDemo", JOptionPane.INFORMATION_MESSAGE);
    }

    
    /** To close the application from window menu (when running as app) */
    private static class WL extends WindowAdapter {
        public void windowClosing(WindowEvent e) {
            System.exit(0);
        }
    }

    public static void main(String[] args) {
    final SpheRayDemo applet = new SpheRayDemo();
    JFrame myFrame = new JFrame("Spherical Ray Trace Demonstration");
    /*myFrame.addWindowListener(new WL()); */
    myFrame.addWindowListener(new WindowAdapter() {
        public void windowIconified(WindowEvent e) {
            applet.stop();
        }
        public void windowDeiconified(WindowEvent e) {
            applet.start();
        }
        public void windowClosing(WindowEvent e) {
            System.exit(0);
        }
    });
    myFrame.add(applet, BorderLayout.CENTER);
    myFrame.setSize(700,570);
    //myFrame.setSize(new Dimension(505, 690));
    applet.init();
    applet.start();
    myFrame.setVisible(true);
    }









    /** Thread-handling class for running calculation without freezing up the UI */
    private class CalcThread extends Thread {
        CalcThread() {
            this.start();
        }
        public void run() {
            calcRays();
        }
    }



    private void calcRays() {
        double t, p;
        double currentRayAngle, currentC, currentR, currentPhi=0.0;
        double deltaR, deltaPhi, lastR, lastC;
        int r;
        
        // convert textfield entries to numbers
        double startAngle = Double.parseDouble(this.startAngleTxt.getText());
        double timeIncr = Double.parseDouble(this.timeIncrTxt.getText());
        double maxTime = Double.parseDouble(this.maxItersTxt.getText())*timeIncr;
        double endAngle = Double.parseDouble(this.endAngleTxt.getText());
        double angleIncr = Double.parseDouble(this.angleIncrTxt.getText());
        
        
        // make sure VZdata is in sorted order for finding surface wavespeed.
        // radius is assumed negative downward, so this should put surface at beginning
        VZData.sort();
        
        // preparation for interpolation below
        VZData.computeLinearGradients();
        
        // calc number of rays from start angle, end angle, and angle increment.
        // we will purposely leave out the 0.0 and 90.0 cases, but will take
        // care of that later.
        int numrays = (int)((startAngle-endAngle)/angleIncr);

        // loop over rays
        for(r=0; r<numrays && stopCalc==false; r++) {
            
            currentPhi=0.0;
            currentR = surfaceRadius - Double.parseDouble(this.srcDepthTxt.getText());
            RPhiData[r] = new DataSeries();
            RPhiData[r].add(currentR*cosd(currentPhi), currentR*sind(currentPhi));
            
            // calc takeoff angle for this ray
            currentRayAngle = startAngle - r*angleIncr;
            
            //System.out.println("on ray "+r+" out of "+numrays+
            //                 " with takeoff angle "+currentRayAngle);
            
            // we don't want angle=0 (straight down), as that won't return to surface
            if(currentRayAngle!=0.0) {
                
                // set initial radius at source radius.  if source radius is surfaceRadius we
                // go down just slightly because of surfaceRadius-check for surface bounce
                if(currentR==surfaceRadius) currentR = surfaceRadius-0.1;

                calcThread.yield();
                
                // get wavespeed at source radius
                currentC = VZData.interpolateY(currentR);
                //System.out.println("retrieved currentC="+currentC+" for currentR="+currentR);
                
                calcThread.yield();

                // calc p for this ray
                p = currentR * sind(currentRayAngle) / currentC;
                double dRsign=-1.0;
                
                // loop over time t for this ray
                for(t=0.0; stopCalc==false && t<maxTime; t+=timeIncr) {
                    
                    // propagate ray forward one timestep:
                    deltaR = (dRsign)*currentC*cosd( currentRayAngle )*timeIncr;
                    lastR = currentR;
                    currentR += deltaR;
                    if( currentR >= surfaceRadius ) {
                        currentR = surfaceRadius-0.1;
                        dRsign *= (-1);
                    }
                    // assuming here that steps are small enough that arc ~ straight line
                    deltaPhi = ( currentC*sind(currentRayAngle)*timeIncr ) / currentR * 180./Math.PI;
                    currentPhi += deltaPhi;

                    // Update wavespeed
                    lastC = currentC;
                    currentC = VZData.interpolateY( currentR ); //interp wavespd 
                    // Update Theta via Snell's law, maintaining sign, because arcsin() won't.
                    // Also, handle limiting case where rays curve back up...
                    double arg = lastR*currentC*sind(currentRayAngle)/(currentR*lastC);
                    if( arg < 1. && arg > 0.) {
                        currentRayAngle = arcsin( arg );
                        //System.out.println("arcsin pos"); 
                        }
                    else if( arg > -1. && arg < 0.) {
                        currentRayAngle = arcsin( arg );
                        //System.out.println("arcsin pos"); 
                        }
                    else if( arg < -1. ) {
                        currentRayAngle = sign(currentRayAngle)*arcsin( -2-arg );
                        dRsign *= (-1);
                        //System.out.println("arcsin turning 1"); 
                        }
                    else if( arg > 1. ) {
                        currentRayAngle = sign(currentRayAngle)*arcsin( 2-arg );
                        dRsign *= (-1);
                        //System.out.println("arcsin turning 2");
                        }

                    //System.out.println(lastR+", "+currentR+", "+lastC+", "+currentC+", "+arg+", "+currentRayAngle+", "+currentPhi);
                    RPhiData[r].add(currentR*cosd(currentPhi), currentR*sind(currentPhi));

                    calcThread.yield();

                }
                
            }

        }
        polarpanel.updateMulti();


        // Pause just a sec before returning to prevent accidental double-click from restarting
        //try{Thread.sleep(2000);} catch(InterruptedException e){}
        
        // "Run" button was changed to "Stop" button during run, so change back now
        stopAction.setEnabled(false);
        runAction.setEnabled(true);
        alreadyRunning=false;
    }


    //from Pete's Math class:
    //conversion factor "degress per radian" */
    private static final double DPR = 180./Math.PI;
    //from Pete's Math class:
    //Purpose: Cosine of angle in degrees
    private static double cosd( double Angle ) { 
    return( Math.cos( Angle/DPR ) );
    }
    //from Pete's Math class:
    //Purpose: Sine of angle in degrees
    private static double sind( double Angle ) {
    return( Math.sin( Angle/DPR ) );
    }
    //from Pete's Math class:
    //Purpose: Tangent of angle in degrees
    //private static double tand( double Angle ) {
    //return( Math.tan( Angle/DPR ) );
    //}
    // from Pete's Math class:
    //Purpose: Arc-Cosine of angle in degrees
    private static double arccos( double arg ) {
    return( Math.acos( arg ) * DPR );
    }
    //from Pete's Math class:
    //Purpose: Arc-Sine of angle in degrees
    private static double arcsin( double arg ) {
    return( Math.asin( arg ) * DPR );
    }
    // from Pete's Math class:
    //Purpose: 2-argument arc-tangent, in degrees
    //private static double arctan( double y, double x ) {
    //double temp = Math.atan2( y, x ) * DPR;
    //return( temp < 0.0 ? temp+360. : temp );
    //}
    //from Pete's Math class:
    //Purpose: Determine sign of a double,int.  Return + or - 1 as per sign.
    private static int sign( double x ) { return ( x >=0 ? 1:-1 ); }
    private static int sign( int x ) { return sign( (double)x ); }


    public String[][] getParameterInfo() {
        String[][] info = {
           // Parameter Name     Kind of Value   Description
           {"demowidth",         "int",          "desired pixel width of applet"},
           {"plotmode",          "String",       "VZ-ZX, VZ-ZX-TX, VZ-ZX-TX-PX-TP"}
        };
        return info;
    }

}
