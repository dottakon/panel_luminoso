import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.Random;

/**
 * CartelLuminoso - Cartel animado estilo Cyberpunk / Hacker / Matrix
 * Muestra "DOTTAKON" y "HACKER" con estética neón verde.
 *
 * Compilar: javac CartelLuminoso.java
 * Ejecutar: java CartelLuminoso
 *
 * v2 — Optimizado: caché de primitivas gráficas, textura estática
 *       pre-renderizada, métricas de texto calculadas una sola vez.
 */
public class CartelLuminoso extends JPanel implements ActionListener {

    // --- Dimensiones ---
    private static final int ANCHO = 1000;
    private static final int ALTO = 700;

    // --- Textos ---
    private static final String TITULO = "DOTTAKON";
    private static final String SUBTITULO = "HACKER";

    // --- Colores (constantes estáticas, coste cero) ---
    private static final Color NEGRO = new Color(5, 5, 8);
    private static final Color VERDE_NEON = new Color(0, 255, 65);
    private static final Color VERDE_NEON_BRILLANTE = new Color(120, 255, 160);
    private static final Color VERDE_OSCURO = new Color(0, 80, 20);
    private static final Color VERDE_MUY_OSCURO = new Color(0, 40, 10);

    // --- Lluvia digital ---
    private static final int NUM_COLUMNAS = 60;
    private static final String CHARS_MATRIX =
            "アイウエオカキクケコサシスセソタチツテトナニヌネノハヒフヘホマミムメモヤユヨラリルレロワヲン0123456789ABCDEF";
    private final int[] lluviaY;
    private final int[] lluviaVelocidad;
    private final int[] lluviaLongitud;
    private final char[][] lluviaChars;

    // --- Partículas flotantes ---
    private static final int NUM_PARTICULAS = 40;
    private final float[] partX, partY, partVx, partVy, partAlpha;

    // --- Animación ---
    private final Timer timer;
    private final Random random;
    private int frameCont = 0;

    // --- Parpadeo neón ---
    private float intensidadNeon = 1.0f;
    private float intensidadObjetivo = 1.0f;
    private int contadorParpadeo = 0;

    // --- Fuentes (inicializadas una vez en constructor) ---
    private final Font fuenteTitulo;
    private final Font fuenteSubtitulo;
    private final Font fuenteMatrix;
    private final Font fuenteMarco;

    // --- Caché de primitivas gráficas (cero allocations en render loop) ---
    private final BufferedImage texturaEstatica;
    private final BasicStroke strokeMarco2;
    private final BasicStroke strokeMarco3;
    private final BasicStroke strokeCruceta;
    private final BasicStroke strokeLineaDecorativa;
    private final float glowAlpha1 = 0.08f;
    private final float glowAlpha2 = 0.12f;
    private final float glowAlpha3 = 0.20f;
    private final AlphaComposite compositeOpaco;

    // --- Caché de métricas de texto ---
    private final int xTituloCache;
    private final int yTituloCache;
    private final int xSubtituloCache;
    private final int ySubtituloCache;
    private final int lineaDecoW;
    private final int lineaDecoX;

    // --- Caché de transform para reflejo ---
    private final AffineTransform transformReflejo;
    private final int yReflejoBase;

    public CartelLuminoso() {
        setPreferredSize(new Dimension(ANCHO, ALTO));
        setBackground(NEGRO);
        setDoubleBuffered(true);

        random = new Random(42);

        // =====================================================================
        // 1. FUENTES — Se resuelve la familia una sola vez
        // =====================================================================
        String familia = resolverFuenteMonoespaciada();
        fuenteTitulo = new Font(familia, Font.BOLD, 90);
        fuenteSubtitulo = new Font(familia, Font.BOLD, 42);
        fuenteMatrix = new Font(familia, Font.PLAIN, 14);
        fuenteMarco = new Font(familia, Font.PLAIN, 10);

        // =====================================================================
        // 2. MÉTRICAS DE TEXTO — Calculadas una vez con un Graphics2D temporal
        // =====================================================================
        BufferedImage tmp = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D gTmp = tmp.createGraphics();

        FontMetrics fmTitulo = gTmp.getFontMetrics(fuenteTitulo);
        FontMetrics fmSubtitulo = gTmp.getFontMetrics(fuenteSubtitulo);

        yTituloCache = (int) (ALTO * 0.38);
        xTituloCache = (ANCHO - fmTitulo.stringWidth(TITULO)) / 2;
        ySubtituloCache = yTituloCache + 65;
        xSubtituloCache = (ANCHO - fmSubtitulo.stringWidth(SUBTITULO)) / 2;

        lineaDecoW = fmSubtitulo.stringWidth(SUBTITULO) + 40;
        lineaDecoX = (ANCHO - lineaDecoW) / 2;

        gTmp.dispose();

        // =====================================================================
        // 3. PRIMITIVAS GRÁFICAS CACHEADAS
        // =====================================================================
        strokeMarco2 = new BasicStroke(2);
        strokeMarco3 = new BasicStroke(3);
        strokeCruceta = new BasicStroke(1);
        strokeLineaDecorativa = new BasicStroke(1.5f);
        compositeOpaco = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f);

        // =====================================================================
        // 4. TRANSFORM DEL REFLEJO — Precalculado
        // =====================================================================
        yReflejoBase = ALTO - 120;
        transformReflejo = new AffineTransform();
        transformReflejo.translate(0, yReflejoBase + yTituloCache);
        transformReflejo.scale(1, -1);

        // =====================================================================
        // 5. TEXTURA ESTÁTICA — Fondo + Scanlines + Viñeta (se dibuja una vez)
        // =====================================================================
        texturaEstatica = crearTexturaEstatica();

        // =====================================================================
        // 6. LLUVIA DIGITAL — Estado inicial
        // =====================================================================
        lluviaY = new int[NUM_COLUMNAS];
        lluviaVelocidad = new int[NUM_COLUMNAS];
        lluviaLongitud = new int[NUM_COLUMNAS];
        lluviaChars = new char[NUM_COLUMNAS][30];

        for (int i = 0; i < NUM_COLUMNAS; i++) {
            lluviaY[i] = random.nextInt(ALTO);
            lluviaVelocidad[i] = 2 + random.nextInt(6);
            lluviaLongitud[i] = 8 + random.nextInt(18);
            for (int j = 0; j < lluviaChars[i].length; j++) {
                lluviaChars[i][j] = charMatrixAleatorio();
            }
        }

        // =====================================================================
        // 7. PARTÍCULAS — Estado inicial
        // =====================================================================
        partX = new float[NUM_PARTICULAS];
        partY = new float[NUM_PARTICULAS];
        partVx = new float[NUM_PARTICULAS];
        partVy = new float[NUM_PARTICULAS];
        partAlpha = new float[NUM_PARTICULAS];
        for (int i = 0; i < NUM_PARTICULAS; i++) {
            resetParticula(i);
            partX[i] = random.nextFloat() * ANCHO;
            partY[i] = random.nextFloat() * ALTO;
        }

        // Timer ~30 FPS
        timer = new Timer(33, this);
        timer.start();
    }

    // =========================================================================
    //  INICIALIZACIÓN AUXILIAR
    // =========================================================================

    /** Busca la mejor fuente monoespaciada disponible en el sistema */
    private static String resolverFuenteMonoespaciada() {
        String[] candidatas = {"Consolas", "Lucida Console", "Courier New"};
        for (String nombre : candidatas) {
            Font test = new Font(nombre, Font.BOLD, 20);
            if (test.getFamily().equalsIgnoreCase(nombre)
                    || test.getName().equalsIgnoreCase(nombre)) {
                return nombre;
            }
        }
        return "Monospaced";
    }

    /** Pre-renderiza las capas que nunca cambian: fondo degradado, scanlines y viñeta */
    private BufferedImage crearTexturaEstatica() {
        BufferedImage img = new BufferedImage(ANCHO, ALTO, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Fondo degradado
        g.setPaint(new GradientPaint(0, 0, new Color(8, 12, 8), 0, ALTO, new Color(2, 5, 2)));
        g.fillRect(0, 0, ANCHO, ALTO);

        // Scanlines CRT (~233 líneas dibujadas UNA sola vez)
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.06f));
        g.setColor(Color.BLACK);
        for (int y = 0; y < ALTO; y += 3) {
            g.drawLine(0, y, ANCHO, y);
        }

        // Viñeta oscura en bordes
        g.setComposite(AlphaComposite.SrcOver);
        g.setPaint(new RadialGradientPaint(
                new Point2D.Float(ANCHO / 2f, ALTO / 2f), ANCHO * 0.7f,
                new float[]{0f, 0.7f, 1f},
                new Color[]{
                        new Color(0, 0, 0, 0),
                        new Color(0, 0, 0, 30),
                        new Color(0, 0, 0, 180)
                }));
        g.fillRect(0, 0, ANCHO, ALTO);

        g.dispose();
        return img;
    }

    private char charMatrixAleatorio() {
        return CHARS_MATRIX.charAt(random.nextInt(CHARS_MATRIX.length()));
    }

    private void resetParticula(int i) {
        partX[i] = random.nextFloat() * ANCHO;
        partY[i] = random.nextFloat() * ALTO;
        partVx[i] = (random.nextFloat() - 0.5f) * 1.2f;
        partVy[i] = (random.nextFloat() - 0.5f) * 0.8f;
        partAlpha[i] = 0.2f + random.nextFloat() * 0.5f;
    }

    // =========================================================================
    //  ACTUALIZACIÓN DE ESTADO (lógica pura, cero objetos gráficos)
    // =========================================================================

    @Override
    public void actionPerformed(ActionEvent e) {
        frameCont++;
        actualizarLluvia();
        actualizarParpadeo();
        actualizarParticulas();
        repaint();
    }

    private void actualizarLluvia() {
        for (int i = 0; i < NUM_COLUMNAS; i++) {
            lluviaY[i] += lluviaVelocidad[i];
            if (lluviaY[i] - lluviaLongitud[i] * 16 > ALTO) {
                lluviaY[i] = -random.nextInt(200);
                lluviaVelocidad[i] = 2 + random.nextInt(6);
                lluviaLongitud[i] = 8 + random.nextInt(18);
            }
            if (frameCont % 3 == 0) {
                int idx = random.nextInt(lluviaChars[i].length);
                lluviaChars[i][idx] = charMatrixAleatorio();
            }
        }
    }

    private void actualizarParpadeo() {
        contadorParpadeo++;
        if (contadorParpadeo > 60 + random.nextInt(120)) {
            contadorParpadeo = 0;
            intensidadObjetivo = random.nextFloat() < 0.3f
                    ? 0.3f + random.nextFloat() * 0.3f
                    : 0.85f + random.nextFloat() * 0.15f;
        }
        intensidadNeon += (intensidadObjetivo - intensidadNeon) * 0.15f;
        if (contadorParpadeo > 5) {
            intensidadObjetivo += (1.0f - intensidadObjetivo) * 0.08f;
        }
    }

    private void actualizarParticulas() {
        for (int i = 0; i < NUM_PARTICULAS; i++) {
            partX[i] += partVx[i];
            partY[i] += partVy[i];
            partAlpha[i] -= 0.002f;
            if (partX[i] < 0 || partX[i] > ANCHO
                    || partY[i] < 0 || partY[i] > ALTO
                    || partAlpha[i] <= 0) {
                resetParticula(i);
            }
        }
    }

    // =========================================================================
    //  RENDERIZADO
    // =========================================================================

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();

        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);

        // 1. Textura estática (fondo + scanlines + viñeta) — un solo blit
        g2.drawImage(texturaEstatica, 0, 0, null);

        // 2. Pulso dinámico central
        dibujarPulsoCentral(g2);

        // 3. Elementos dinámicos
        dibujarLluviaDigital(g2);
        dibujarParticulas(g2);
        dibujarMarco(g2);
        dibujarReflejo(g2);
        dibujarTextoNeon(g2);

        g2.dispose();
    }

    /** Resplandor central con pulso sinusoidal */
    private void dibujarPulsoCentral(Graphics2D g2) {
        float pulso = 0.3f + 0.1f * (float) Math.sin(frameCont * 0.03);
        int gVal = (int) (60 * pulso);
        int aVal = (int) (40 * pulso);
        RadialGradientPaint rgp = new RadialGradientPaint(
                new Point2D.Float(ANCHO / 2f, ALTO * 0.38f), 300,
                new float[]{0f, 1f},
                new Color[]{new Color(0, gVal, 0, aVal), new Color(0, 0, 0, 0)});
        g2.setPaint(rgp);
        g2.fillRect(0, 0, ANCHO, ALTO);
    }

    /** Lluvia de caracteres estilo Matrix */
    private void dibujarLluviaDigital(Graphics2D g2) {
        g2.setFont(fuenteMatrix);
        int espacioColumna = ANCHO / NUM_COLUMNAS;

        for (int i = 0; i < NUM_COLUMNAS; i++) {
            int x = i * espacioColumna + espacioColumna / 2;
            int longitud = Math.min(lluviaLongitud[i], lluviaChars[i].length);

            for (int j = 0; j < longitud; j++) {
                int y = lluviaY[i] - j * 16;
                if (y < -16 || y > ALTO + 16) continue;

                if (j == 0) {
                    g2.setColor(VERDE_NEON_BRILLANTE);
                } else {
                    float alpha = Math.max(0, 1.0f - (float) j / longitud);
                    int verde = Math.min(255, (int) (180 * alpha + 40));
                    g2.setColor(new Color(0, verde, 0, (int) (alpha * 180)));
                }

                g2.drawString(String.valueOf(lluviaChars[i][j]), x, y);
            }
        }
    }

    /** Partículas flotantes verdes */
    private void dibujarParticulas(Graphics2D g2) {
        for (int i = 0; i < NUM_PARTICULAS; i++) {
            int alpha = (int) (partAlpha[i] * 120);
            if (alpha <= 0) continue;
            g2.setColor(new Color(0, 255, 65, Math.min(255, alpha)));
            int tam = 1 + (int) (partAlpha[i] * 3);
            g2.fillOval((int) partX[i], (int) partY[i], tam, tam);
        }
    }

    /** Marco futurista con esquinas cortadas estilo HUD */
    private void dibujarMarco(Graphics2D g2) {
        int margen = 20;
        int esquina = 30;
        int x1 = margen, y1 = margen;
        int x2 = ANCHO - margen, y2 = ALTO - margen;

        Composite original = g2.getComposite();
        float alphaMarco = 0.6f + 0.2f * intensidadNeon;
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alphaMarco));

        // Líneas principales
        g2.setStroke(strokeMarco2);
        g2.setColor(VERDE_OSCURO);
        g2.drawLine(x1 + esquina, y1, x2 - esquina, y1);
        g2.drawLine(x1 + esquina, y2, x2 - esquina, y2);
        g2.drawLine(x1, y1 + esquina, x1, y2 - esquina);
        g2.drawLine(x2, y1 + esquina, x2, y2 - esquina);

        // Esquinas diagonales brillantes
        g2.setColor(VERDE_NEON);
        g2.setStroke(strokeMarco3);
        g2.drawLine(x1, y1 + esquina, x1 + esquina, y1);
        g2.drawLine(x2 - esquina, y1, x2, y1 + esquina);
        g2.drawLine(x1, y2 - esquina, x1 + esquina, y2);
        g2.drawLine(x2 - esquina, y2, x2, y2 - esquina);

        // Crucetas decorativas
        g2.setStroke(strokeCruceta);
        g2.setColor(VERDE_OSCURO);
        int marca = 8;
        dibujarCruceta(g2, x1 + esquina / 2, y1 + esquina / 2, marca);
        dibujarCruceta(g2, x2 - esquina / 2, y1 + esquina / 2, marca);
        dibujarCruceta(g2, x1 + esquina / 2, y2 - esquina / 2, marca);
        dibujarCruceta(g2, x2 - esquina / 2, y2 - esquina / 2, marca);

        // Texto decorativo
        g2.setFont(fuenteMarco);
        g2.setColor(new Color(0, 120, 30, 150));
        g2.drawString("SYS://DOTTAKON.PANEL.v2.4", x1 + esquina + 10, y1 + 14);
        g2.drawString("[SECURE CONNECTION]", x2 - 160, y1 + 14);

        // Indicador de estado con parpadeo
        g2.setColor(frameCont % 60 < 40 ? VERDE_NEON : VERDE_OSCURO);
        g2.drawString("\u25CF ONLINE", x1 + esquina + 10, y2 - 8);

        g2.setColor(new Color(0, 120, 30, 150));
        g2.drawString("MEM:" + String.format("0x%06X", (frameCont * 7919) & 0xFFFFFF),
                x2 - 130, y2 - 8);

        g2.setComposite(original);
    }

    private void dibujarCruceta(Graphics2D g2, int cx, int cy, int tam) {
        g2.drawLine(cx - tam, cy, cx + tam, cy);
        g2.drawLine(cx, cy - tam, cx, cy + tam);
    }

    /** Texto principal con efecto neón y glow multicapa */
    private void dibujarTextoNeon(Graphics2D g2) {
        // --- TITULO ---
        g2.setFont(fuenteTitulo);
        dibujarGlow(g2, TITULO, xTituloCache, yTituloCache, fuenteTitulo, intensidadNeon);

        int greenVal = Math.min(255, (int) (255 * intensidadNeon));
        g2.setColor(new Color(
                (int) (100 * intensidadNeon), greenVal, (int) (60 * intensidadNeon)));
        g2.drawString(TITULO, xTituloCache, yTituloCache);

        // Highlight central (tubo de neón)
        g2.setColor(new Color(
                (int) (200 * intensidadNeon), 255, (int) (200 * intensidadNeon),
                (int) (120 * intensidadNeon)));
        g2.drawString(TITULO, xTituloCache, yTituloCache);

        // --- SUBTITULO ---
        g2.setFont(fuenteSubtitulo);
        dibujarGlow(g2, SUBTITULO, xSubtituloCache, ySubtituloCache,
                fuenteSubtitulo, intensidadNeon * 0.8f);

        g2.setColor(new Color(0, (int) (220 * intensidadNeon), (int) (40 * intensidadNeon)));
        g2.drawString(SUBTITULO, xSubtituloCache, ySubtituloCache);

        g2.setColor(new Color(
                (int) (150 * intensidadNeon), 255, (int) (150 * intensidadNeon),
                (int) (80 * intensidadNeon)));
        g2.drawString(SUBTITULO, xSubtituloCache, ySubtituloCache);

        // Línea decorativa bajo subtítulo
        float lineAlpha = Math.min(1f, 0.4f + 0.3f * intensidadNeon);
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, lineAlpha));
        g2.setStroke(strokeLineaDecorativa);
        g2.setColor(VERDE_NEON);
        int yLinea = ySubtituloCache + 15;
        g2.drawLine(lineaDecoX, yLinea, lineaDecoX + lineaDecoW, yLinea);
        g2.fillOval(lineaDecoX - 3, yLinea - 3, 6, 6);
        g2.fillOval(lineaDecoX + lineaDecoW - 3, yLinea - 3, 6, 6);
        g2.setComposite(compositeOpaco);
    }

    /** Glow difuso en 3 capas con alphas precalculados */
    private void dibujarGlow(Graphics2D g2, String texto, int x, int y,
                             Font fuente, float intensidad) {
        g2.setFont(fuente);
        Composite original = g2.getComposite();
        g2.setColor(VERDE_NEON);

        // Capa 1: glow amplio
        g2.setComposite(AlphaComposite.getInstance(
                AlphaComposite.SRC_OVER, glowAlpha1 * intensidad));
        for (int dx = -4; dx <= 4; dx += 2) {
            for (int dy = -4; dy <= 4; dy += 2) {
                g2.drawString(texto, x + dx, y + dy);
            }
        }

        // Capa 2: glow medio
        g2.setComposite(AlphaComposite.getInstance(
                AlphaComposite.SRC_OVER, glowAlpha2 * intensidad));
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                g2.drawString(texto, x + dx, y + dy);
            }
        }

        // Capa 3: glow cercano
        g2.setComposite(AlphaComposite.getInstance(
                AlphaComposite.SRC_OVER, glowAlpha3 * intensidad));
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                g2.drawString(texto, x + dx, y + dy);
            }
        }

        g2.setComposite(original);
    }

    /** Reflejo invertido del texto usando transform precalculado */
    private void dibujarReflejo(Graphics2D g2) {
        Graphics2D g2r = (Graphics2D) g2.create();
        g2r.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2r.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
        g2r.setTransform(transformReflejo);

        // Título reflejado
        float alphaRef = 0.08f * intensidadNeon;
        g2r.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alphaRef));
        g2r.setFont(fuenteTitulo);
        g2r.setColor(VERDE_OSCURO);
        g2r.drawString(TITULO, xTituloCache, yTituloCache);

        // Subtítulo reflejado
        g2r.setComposite(AlphaComposite.getInstance(
                AlphaComposite.SRC_OVER, alphaRef * 0.6f));
        g2r.setFont(fuenteSubtitulo);
        g2r.drawString(SUBTITULO, xSubtituloCache, ySubtituloCache);

        g2r.dispose();

        // Línea de "suelo"
        Composite original = g2.getComposite();
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.15f));

        g2.setPaint(new GradientPaint(
                ANCHO * 0.2f, yReflejoBase, new Color(0, 0, 0, 0),
                ANCHO * 0.5f, yReflejoBase, VERDE_MUY_OSCURO));
        g2.fillRect((int) (ANCHO * 0.2), yReflejoBase - 1, (int) (ANCHO * 0.3), 2);

        g2.setPaint(new GradientPaint(
                ANCHO * 0.5f, yReflejoBase, VERDE_MUY_OSCURO,
                ANCHO * 0.8f, yReflejoBase, new Color(0, 0, 0, 0)));
        g2.fillRect((int) (ANCHO * 0.5), yReflejoBase - 1, (int) (ANCHO * 0.3), 2);

        g2.setComposite(original);
    }

    // =========================================================================
    //  MAIN
    // =========================================================================

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("DOTTAKON // HACKER PANEL");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setResizable(false);

            CartelLuminoso panel = new CartelLuminoso();
            frame.add(panel);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.getContentPane().setBackground(NEGRO);
            frame.setVisible(true);
        });
    }
}