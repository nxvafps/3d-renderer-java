import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import java.io.File;

public class DemoViewer {

    private static double zoom = 1.0;
    private static double panX = 0;
    private static double panY = 0;

    private static final Color LIGHT_COLOR = Color.WHITE;
    private static final double AMBIENT_LIGHT = 0.2;
    private static final double DIFFUSE_LIGHT = 1.0;
    private static final double SPECULAR_LIGHT = 0.5;
    private static final double SHININESS = 32.0;
    private static final Vertex LIGHT_DIRECTION = new Vertex(0, 0, -1);
    private static final double GAMMA = 2.2;

    private static String lightingMode = "ambient";
    private static final int SUPERSAMPLING_FACTOR = 2;

    public static void main(String[] args) {
        JFrame frame = createFrame();
        Container pane = frame.getContentPane();
        pane.setLayout(new BorderLayout());

        JSlider headingSlider = createHeadingSlider();
        JSlider pitchSlider = createPitchSlider();
        JPanel renderPanel = createRenderPanel(headingSlider, pitchSlider);

        JPanel bottomPanel = createBottomPanel(headingSlider, renderPanel);
        pane.add(bottomPanel, BorderLayout.SOUTH);
        pane.add(pitchSlider, BorderLayout.EAST);
        pane.add(renderPanel, BorderLayout.CENTER);

        JPanel controlPanel = createControlPanel(renderPanel, headingSlider, pitchSlider);
        pane.add(controlPanel, BorderLayout.NORTH);

        frame.setSize(400, 400);
        frame.setVisible(true);
    }

    private static JFrame createFrame() {
        JFrame frame = new JFrame();
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        return frame;
    }

    private static JSlider createHeadingSlider() {
        JSlider headingSlider = new JSlider(0, 360, 180);
        headingSlider.addChangeListener(e -> {
            JSlider source = (JSlider) e.getSource();
            SwingUtilities.getWindowAncestor(source).repaint();
        });
        return headingSlider;
    }

    private static JSlider createPitchSlider() {
        JSlider pitchSlider = new JSlider(SwingConstants.VERTICAL, -90, 90, 0);
        pitchSlider.addChangeListener(e -> {
            JSlider source = (JSlider) e.getSource();
            SwingUtilities.getWindowAncestor(source).repaint();
        });
        return pitchSlider;
    }

    private static JPanel createRenderPanel(JSlider headingSlider, JSlider pitchSlider) {
        JPanel renderPanel = new JPanel() {
            public void paintComponent(Graphics g) {
                super.paintComponent(g);
                renderScene(g, headingSlider.getValue(), pitchSlider.getValue());
            }
        };
        addMouseListeners(renderPanel);
        return renderPanel;
    }

    private static JPanel createBottomPanel(JSlider headingSlider, JPanel renderPanel) {
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(headingSlider, BorderLayout.NORTH);

        JButton exportButton = new JButton("Export Image");
        exportButton.addActionListener(e -> exportImage(renderPanel));
        bottomPanel.add(exportButton, BorderLayout.SOUTH);

        return bottomPanel;
    }

    private static JPanel createControlPanel(JPanel renderPanel, JSlider headingSlider, JSlider pitchSlider) {
        JPanel controlPanel = new JPanel();
        ButtonGroup lightingGroup = new ButtonGroup();

        JRadioButton ambientButton = new JRadioButton("Ambient", true);
        ambientButton.addActionListener(e -> {
            lightingMode = "ambient";
            renderPanel.repaint();
        });

        JRadioButton diffuseButton = new JRadioButton("Diffuse");
        diffuseButton.addActionListener(e -> {
            lightingMode = "diffuse";
            renderPanel.repaint();
        });

        lightingGroup.add(ambientButton);
        lightingGroup.add(diffuseButton);

        controlPanel.add(ambientButton);
        controlPanel.add(diffuseButton);

        JButton resetButton = new JButton("Reset View");
        resetButton.addActionListener(e -> {
            zoom = 1.0;
            panX = 0;
            panY = 0;
            headingSlider.setValue(180);
            pitchSlider.setValue(0);
            renderPanel.repaint();
        });
        controlPanel.add(resetButton);

        return controlPanel;
    }

    private static void addMouseListeners(JPanel renderPanel) {
        renderPanel.addMouseWheelListener(e -> {
            zoom += e.getPreciseWheelRotation() * -0.1;
            zoom = Math.max(0.1, zoom);
            renderPanel.repaint();
        });

        renderPanel.addMouseMotionListener(new MouseAdapter() {
            private int lastX, lastY;

            @Override
            public void mousePressed(MouseEvent e) {
                lastX = e.getX();
                lastY = e.getY();
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                int deltaX = e.getX() - lastX;
                int deltaY = e.getY() - lastY;
                panX += deltaX / zoom;
                panY += deltaY / zoom;
                lastX = e.getX();
                lastY = e.getY();
                renderPanel.repaint();
            }
        });
    }

    private static void renderScene(Graphics g, int heading, int pitch) {
        Graphics2D g2 = (Graphics2D) g;
        g2.setColor(Color.BLACK);
        g2.fillRect(0, 0, g.getClipBounds().width, g.getClipBounds().height);

        List<Triangle> tris = createTriangles();
        Matrix3 transform = createTransformMatrix(heading, pitch);

        int width = g.getClipBounds().width * SUPERSAMPLING_FACTOR;
        int height = g.getClipBounds().height * SUPERSAMPLING_FACTOR;
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        double[] zBuffer = initializeZBuffer(img);

        renderTriangles(tris, transform, img, zBuffer);

        BufferedImage finalImage = new BufferedImage(g.getClipBounds().width, g.getClipBounds().height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D gFinal = finalImage.createGraphics();
        gFinal.drawImage(img, 0, 0, g.getClipBounds().width, g.getClipBounds().height, null);
        gFinal.dispose();

        g2.drawImage(finalImage, 0, 0, null);
    }

    private static void exportImage(JPanel renderPanel) {
        BufferedImage img = new BufferedImage(renderPanel.getWidth(), renderPanel.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        renderPanel.paint(g2);
        g2.dispose();
        try {
            File outputFile = new File("rendered_image.png");
            ImageIO.write(img, "png", outputFile);
            JOptionPane.showMessageDialog(renderPanel.getParent(), "Image exported successfully to: " + outputFile.getAbsolutePath());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(renderPanel.getParent(), "Error exporting image: " + ex.getMessage());
        }
    }

    private static List<Triangle> createTriangles() {
        List<Triangle> tris = new ArrayList<>();
        tris.add(new Triangle(new Vertex(100, 100, 100),
                              new Vertex(-100, -100, 100),
                              new Vertex(-100, 100, -100),
                              Color.WHITE));
        tris.add(new Triangle(new Vertex(100, 100, 100),
                              new Vertex(-100, -100, 100),
                              new Vertex(100, -100, -100),
                              Color.RED));
        tris.add(new Triangle(new Vertex(-100, 100, -100),
                              new Vertex(100, -100, -100),
                              new Vertex(100, 100, 100),
                              Color.GREEN));
        tris.add(new Triangle(new Vertex(-100, 100, -100),
                              new Vertex(100, -100, -100),
                              new Vertex(-100, -100, 100),
                              Color.BLUE));
        return tris;
    }

    private static Matrix3 createTransformMatrix(int headingValue, int pitchValue) {
        double heading = Math.toRadians(headingValue);
        double pitch = Math.toRadians(pitchValue);
        Matrix3 headingTransform = new Matrix3(new double[] {
            Math.cos(heading), 0, Math.sin(heading),
            0, 1, 0,
            -Math.sin(heading), 0, Math.cos(heading)
        });
        Matrix3 pitchTransform = new Matrix3(new double[] {
            1, 0, 0,
            0, Math.cos(pitch), Math.sin(pitch),
            0, -Math.sin(pitch), Math.cos(pitch)
        });
        return headingTransform.multiply(pitchTransform);
    }

    private static double[] initializeZBuffer(BufferedImage img) {
        double[] zBuffer = new double[img.getWidth() * img.getHeight()];
        for (int q = 0; q < zBuffer.length; q++) {
            zBuffer[q] = Double.NEGATIVE_INFINITY;
        }
        return zBuffer;
    }

    private static void renderTriangles(List<Triangle> tris, Matrix3 transform, BufferedImage img, double[] zBuffer) {
        for (Triangle t : tris) {
            Vertex v1 = transform.transform(t.v1);
            Vertex v2 = transform.transform(t.v2);
            Vertex v3 = transform.transform(t.v3);

            // apply zoom
            applyZoom(v1);
            applyZoom(v2);
            applyZoom(v3);

            // translate vertices
            translateVertex(v1, img);
            translateVertex(v2, img);
            translateVertex(v3, img);

            // calculate normal
            Vertex norm = calculateNormal(v1, v2, v3);
            double angleCos = Math.abs(norm.z);

            // compute lighting
            Color shadedColor = computeLighting(t.color, norm, v1);

            // compute rectangular bounds for triangle
            int[] bounds = computeBounds(v1, v2, v3, img);
            int minX = bounds[0], maxX = bounds[1], minY = bounds[2], maxY = bounds[3];

            double triangleArea = calculateTriangleArea(v1, v2, v3);

            rasterizeTriangle(v1, v2, v3, shadedColor, angleCos, img, zBuffer, minX, maxX, minY, maxY, triangleArea);
        }
    }

    private static Color computeLighting(Color baseColor, Vertex normal, Vertex position) {
        double ambient = AMBIENT_LIGHT;
        double diffuse = 0;
        double specular = 0;

        switch (lightingMode) {
            case "ambient":
                return applyGammaCorrection(getShade(baseColor, ambient));
            case "diffuse":
                diffuse = DIFFUSE_LIGHT * Math.max(0, dotProduct(normal, LIGHT_DIRECTION));
                return applyGammaCorrection(getShade(baseColor, ambient + diffuse));
            case "specular":
                Vertex viewDirection = new Vertex(0, 0, 1); // Assuming the viewer is along the z-axis
                Vertex reflection = reflect(LIGHT_DIRECTION, normal);
                specular = SPECULAR_LIGHT * Math.pow(Math.max(0, dotProduct(viewDirection, reflection)), SHININESS);
                return applyGammaCorrection(getShade(baseColor, ambient + diffuse + specular));
        }

        return baseColor;
    }

    private static double dotProduct(Vertex v1, Vertex v2) {
        return v1.x * v2.x + v1.y * v2.y + v1.z * v2.z;
    }

    private static Vertex reflect(Vertex light, Vertex normal) {
        double dot = dotProduct(light, normal);
        return new Vertex(
            light.x - 2 * dot * normal.x,
            light.y - 2 * dot * normal.y,
            light.z - 2 * dot * normal.z
        );
    }

    private static void applyZoom(Vertex v) {
        v.x *= zoom * SUPERSAMPLING_FACTOR;
        v.y *= zoom * SUPERSAMPLING_FACTOR;
        v.x += panX * SUPERSAMPLING_FACTOR;
        v.y += panY * SUPERSAMPLING_FACTOR;
    }

    private static void translateVertex(Vertex v, BufferedImage img) {
        v.x += img.getWidth() / 2;
        v.y += img.getHeight() / 2;
    }

    private static Vertex calculateNormal(Vertex v1, Vertex v2, Vertex v3) {
        Vertex ab = new Vertex(v2.x - v1.x, v2.y - v1.y, v2.z - v1.z);
        Vertex ac = new Vertex(v3.x - v1.x, v3.y - v1.y, v3.z - v1.z);
        Vertex norm = new Vertex(
            ab.y * ac.z - ab.z * ac.y,
            ab.z * ac.x - ab.x * ac.z,
            ab.x * ac.y - ab.y * ac.x
        );
        double normalLength = Math.sqrt(norm.x * norm.x + norm.y * norm.y + norm.z * norm.z);
        norm.x /= normalLength;
        norm.y /= normalLength;
        norm.z /= normalLength;
        return norm;
    }

    private static int[] computeBounds(Vertex v1, Vertex v2, Vertex v3, BufferedImage img) {
        int minX = (int) Math.max(0, Math.ceil(Math.min(v1.x, Math.min(v2.x, v3.x))));
        int maxX = (int) Math.min(img.getWidth() - 1, Math.floor(Math.max(v1.x, Math.max(v2.x, v3.x))));
        int minY = (int) Math.max(0, Math.ceil(Math.min(v1.y, Math.min(v2.y, v3.y))));
        int maxY = (int) Math.min(img.getHeight() - 1, Math.floor(Math.max(v1.y, Math.max(v2.y, v3.y))));
        return new int[] { minX, maxX, minY, maxY };
    }

    private static double calculateTriangleArea(Vertex v1, Vertex v2, Vertex v3) {
        return (v1.y - v3.y) * (v2.x - v3.x) + (v2.y - v3.y) * (v3.x - v1.x);
    }

    private static void rasterizeTriangle(Vertex v1, Vertex v2, Vertex v3, Color color, double angleCos, BufferedImage img, double[] zBuffer, int minX, int maxX, int minY, int maxY, double triangleArea) {
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                double b1 = ((y - v3.y) * (v2.x - v3.x) + (v2.y - v3.y) * (v3.x - x)) / triangleArea;
                double b2 = ((y - v1.y) * (v3.x - v1.x) + (v3.y - v1.y) * (v1.x - x)) / triangleArea;
                double b3 = ((y - v2.y) * (v1.x - v2.x) + (v1.y - v2.y) * (v2.x - x)) / triangleArea;
                if (b1 >= 0 && b1 <= 1 && b2 >= 0 && b2 <= 1 && b3 >= 0 && b3 <= 1) {
                    double depth = b1 * v1.z + b2 * v2.z + b3 * v3.z;
                    int zIndex = y * img.getWidth() + x;
                    if (zBuffer[zIndex] < depth) {
                        img.setRGB(x, y, getShade(color, angleCos).getRGB());
                        zBuffer[zIndex] = depth;
                    }
                }
            }
        }
    }

    public static Color getShade(Color color, double shade) {
        double redLinear = Math.pow(color.getRed() / 255.0, 2.4) * shade;
        double greenLinear = Math.pow(color.getGreen() / 255.0, 2.4) * shade;
        double blueLinear = Math.pow(color.getBlue() / 255.0, 2.4) * shade;

        int red = (int) (Math.pow(redLinear, 1 / 2.4) * 255);
        int green = (int) (Math.pow(greenLinear, 1 / 2.4) * 255);
        int blue = (int) (Math.pow(blueLinear, 1 / 2.4) * 255);

        // Clamp color values to the range 0-255
        red = Math.max(0, Math.min(255, red));
        green = Math.max(0, Math.min(255, green));
        blue = Math.max(0, Math.min(255, blue));

        return new Color(red, green, blue);
    }

    private static Color applyGammaCorrection(Color color) {
        int red = (int) (255 * Math.pow(color.getRed() / 255.0, 1.0 / GAMMA));
        int green = (int) (255 * Math.pow(color.getGreen() / 255.0, 1.0 / GAMMA));
        int blue = (int) (255 * Math.pow(color.getBlue() / 255.0, 1.0 / GAMMA));

        // Clamp color values to the range 0-255
        red = Math.max(0, Math.min(255, red));
        green = Math.max(0, Math.min(255, green));
        blue = Math.max(0, Math.min(255, blue));

        return new Color(red, green, blue);
    }
}

class Vertex {
    double x;
    double y;
    double z;
    Vertex(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }
}

class Triangle {
    Vertex v1;
    Vertex v2;
    Vertex v3;
    Color color;
    Triangle(Vertex v1, Vertex v2, Vertex v3, Color color) {
        this.v1 = v1;
        this.v2 = v2;
        this.v3 = v3;
        this.color = color;
    }
}

class Matrix3 {
    double[] values;
    Matrix3(double[] values) {
        this.values = values;
    }
    Matrix3 multiply(Matrix3 other) {
        double[] result = new double[9];
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                for (int i = 0; i < 3; i++) {
                    result[row * 3 + col] +=
                        this.values[row * 3 + i] * other.values[i * 3 + col];
                }
            }
        }
        return new Matrix3(result);
    }
    Vertex transform(Vertex in) {
        return new Vertex(
            in.x * values[0] + in.y * values[3] + in.z * values[6],
            in.x * values[1] + in.y * values[4] + in.z * values[7],
            in.x * values[2] + in.y * values[5] + in.z * values[8]
        );
    }
}