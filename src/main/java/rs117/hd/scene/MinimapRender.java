package rs117.hd.scene;

import net.runelite.api.*;
import rs117.hd.HdPluginConfig;
import rs117.hd.config.MinimapType;

import javax.inject.Inject;
import java.awt.*;

public class MinimapRender {

    private final int SPECIAL_COLOR = Color.PINK.getRGB();

    @Inject
    private Client client;

    @Inject
    private HdPluginConfig config;

    private final int[] tmpScreenX = new int[6];
    private final int[] tmpScreenY = new int[6];

    static int blend(int var0, int var1) {
        var1 = (var0 & 127) * var1 >> 7;
        var1 = Math.max(2, var1);
        var1 = Math.min(126, var1);
        return (var0 & 0xFF80) + var1;
    }

    public void drawTile(Tile tile, int tx, int ty, int px0, int py0, int px1, int py1) {
        client.getRasterizer().setRasterGouraudLowRes(false);
        try {
            if (config.minimapType() == MinimapType.SHADED) {
                SceneTilePaint paint = tile.getSceneTilePaint();
                if (paint != null) {
                    int tex = paint.getTexture();
                    if (tex == -1) {
                        int nw = paint.getNwColor();
                        if (nw != 12345678) {
                            fillGradient(px0, py0, px1, py1, nw, paint.getNeColor(), paint.getSwColor(), paint.getSeColor());
                        } else {
                            client.getRasterizer().fillRectangle(px0, py0, px1 - px0, py1 - py0, paint.getRBG());
                        }
                    } else {
                        int hsl = client.getTextureProvider().getDefaultColor(tex);
                        fillGradient(px0, py0, px1, py1, blend(hsl, paint.getNwColor()), blend(hsl, paint.getNeColor()), blend(hsl, paint.getSwColor()), blend(hsl, paint.getSeColor()));
                    }
                    return;
                }

                SceneTileModel stm = tile.getSceneTileModel();
                if (stm == null) {
                    return;
                }

                int[] vertexX = stm.getVertexX();
                int[] vertexZ = stm.getVertexZ();

                int[] indicies1 = stm.getFaceX();
                int[] indicies2 = stm.getFaceY();
                int[] indicies3 = stm.getFaceZ();

                int[] color1 = stm.getTriangleColorA();
                int[] color2 = stm.getTriangleColorB();
                int[] color3 = stm.getTriangleColorC();
                int[] textures = stm.getTriangleTextureId();

                int localX = tx << Perspective.LOCAL_COORD_BITS;
                int localY = ty << Perspective.LOCAL_COORD_BITS;

                int w = px1 - px0;
                int h = py1 - py0;

                for (int vert = 0; vert < vertexX.length; ++vert) {
                    tmpScreenX[vert] = px0 + (((vertexX[vert] - localX) * w) >> Perspective.LOCAL_COORD_BITS);
                    tmpScreenY[vert] = py0 + (((Perspective.LOCAL_TILE_SIZE - (vertexZ[vert] - localY)) * h) >> Perspective.LOCAL_COORD_BITS);
                }

                for (int face = 0; face < indicies1.length; ++face) {
                    int idx1 = indicies1[face];
                    int idx2 = indicies2[face];
                    int idx3 = indicies3[face];

                    int c1 = color1[face];
                    int c2 = color2[face];
                    int c3 = color3[face];
                    if (textures != null && textures[face] != -1) {
                        int hsl = client.getTextureProvider().getDefaultColor(textures[face]);
                        client.getRasterizer().rasterGouraud(
                                tmpScreenY[idx1], tmpScreenY[idx2], tmpScreenY[idx3],
                                tmpScreenX[idx1], tmpScreenX[idx2], tmpScreenX[idx3],
                                blend(hsl, c1), blend(hsl, c2), blend(hsl, c3));
                    } else if (c1 != 12345678) {
                        client.getRasterizer().rasterGouraud(
                                tmpScreenY[idx1], tmpScreenY[idx2], tmpScreenY[idx3],
                                tmpScreenX[idx1], tmpScreenX[idx2], tmpScreenX[idx3],
                                c1, c2, c3);
                    }
                }
            } else if (config.minimapType() == MinimapType.HD) {
                client.getRasterizer().fillRectangle(px0, py0, px1 - px0, py1 - py0, SPECIAL_COLOR);
            }
        } finally {
            client.getRasterizer().setRasterGouraudLowRes(true);
        }

    }

    private void fillGradient(int px0, int py0, int px1, int py1, int c00, int c10, int c01, int c11) {
        Rasterizer g3d = client.getRasterizer();
        g3d.rasterGouraud(py0, py0, py1, px0, px1, px0, c00, c10, c01);
        g3d.rasterGouraud(py0, py1, py1, px1, px0, px1, c10, c01, c11);
    }

}
