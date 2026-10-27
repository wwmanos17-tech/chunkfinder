package com.chunkfinder;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.Identifier;

/**
 * Draws the Pharaoh logo. By default it is the gold pharaoh head, drawn as pixels so no image file is needed
 * (icon "Pharaoh" by Delapouite, game-icons.net, CC BY 3.0, https://creativecommons.org/licenses/by/3.0/).
 * With "Pyramids Logo" on it draws the smooth gold pyramids image instead (icon "Egyptian Pyramids" by Delapouite,
 * game-icons.net, CC BY 3.0, recolored gold; assets/chunkfinder/textures/gui/logo.png).
 */
public class LogoRenderer {
    public static final int SIZE = 32;
    private static final Identifier PYRAMIDS = Identifier.of("chunkfinder", "textures/gui/logo.png");
    private static final String DATA = "14,2,1,F8D868;17,2,1,E8C848;11,3,1,F8D878;12,3,1,F8D868;14,3,1,F8C858;17,3,1,E8C848;19,3,2,E8B838;11,4,1,F8D878;12,4,1,F8D868;14,4,1,F8C858;17,4,1,E8C848;19,4,2,E8B838;9,5,2,F8D878;21,5,2,E8B828;13,6,1,F8C858;14,6,3,E8C848;17,6,2,E8B838;9,7,1,F8D868;15,7,1,E8C848;16,7,1,E8C838;22,7,1,D8A828;8,8,1,F8D868;13,8,1,E8C848;15,8,1,E8C838;16,8,1,E8B838;18,8,1,E8B828;23,8,1,D8A818;11,9,1,E8C858;12,9,1,E8C848;15,9,2,E8B838;19,9,1,E8B828;20,9,1,D8A828;7,10,2,F8D868;9,10,1,F8C858;11,10,3,E8C848;14,10,2,E8B838;16,10,3,E8B828;19,10,1,E8A828;20,10,1,D8A818;22,10,1,D8A818;23,10,1,D89818;24,10,1,C89818;13,11,1,E8C838;14,11,1,E8B838;15,11,3,E8B828;18,11,1,E8A828;7,12,1,F8D868;8,12,1,F8C858;9,12,1,E8C858;10,12,1,E8C848;13,12,1,E8B838;14,12,1,E8B828;17,12,1,E8B828;18,12,1,D8A828;21,12,1,D8A818;22,12,3,C89818;14,13,1,E8B828;16,13,1,E8B828;17,13,1,D8A828;6,14,2,F8C858;8,14,3,E8C848;11,14,1,E8B838;15,14,1,E8B828;16,14,1,E8A828;20,14,1,D89818;21,14,3,C89818;24,14,1,C88818;25,14,1,B88818;15,15,1,E8A828;16,15,1,D8A828;8,16,1,E8C848;9,16,1,E8C838;10,16,2,E8B838;12,16,1,E8B828;15,16,1,D8A828;16,16,1,D8A818;19,16,4,C89818;23,16,1,B88818;15,17,1,D8A828;16,17,1,D8A818;20,17,1,C89818;6,18,2,E8C848;9,18,1,E8B838;10,18,2,E8B828;15,18,2,D8A818;20,18,1,C89818;21,18,1,C88818;22,18,1,B88818;24,18,2,B88818;6,19,1,E8C848;7,19,1,E8C838;9,19,4,E8B828;15,19,1,D8A818;16,19,1,D89818;19,19,2,C89818;21,19,2,B88818;24,19,1,B88818;25,19,1,A87818;5,20,1,E8C848;6,20,1,E8C838;15,20,1,D8A818;25,20,2,A87818;5,21,1,E8C838;6,21,1,E8B838;9,21,2,E8B828;11,21,1,D8A828;12,21,1,D8A818;19,21,1,C88818;20,21,2,B88818;25,21,2,A87818;5,22,2,E8B838;25,22,2,A87818;5,23,1,E8B838;6,23,1,E8B828;10,23,1,D8A828;11,23,2,D8A818;14,23,1,C89818;17,23,1,C89818;19,23,3,B88818;25,23,1,A87818;26,23,1,986818;6,24,2,E8B828;14,24,1,C89818;17,24,1,B88818;24,24,1,A87818;25,24,1,A86818;6,25,2,E8B828;11,25,1,D8A818;12,25,1,C89818;19,25,1,B88818;20,25,1,B87818;24,25,1,A87818;25,25,1,986818;7,26,1,E8A828;8,26,1,D8A818;14,26,2,C89818;16,26,2,B88818;23,26,1,A87818;24,26,1,986818;8,27,2,D8A818;14,27,1,C89818;15,27,3,B88818;21,27,2,A87818;23,27,1,986818;9,28,1,D8A818;10,28,3,C89818;19,28,3,A87818;22,28,1,986818;11,29,2,C89818;13,29,4,B88818;17,29,1,B87818;18,29,3,A87818";

    private static int[][] runs;

    private static void parse() {
        String[] parts = DATA.split(";");
        runs = new int[parts.length][4];
        for (int i = 0; i < parts.length; i++) {
            String[] p = parts[i].split(",");
            runs[i][0] = Integer.parseInt(p[0]);
            runs[i][1] = Integer.parseInt(p[1]);
            runs[i][2] = Integer.parseInt(p[2]);
            runs[i][3] = 0xFF000000 | Integer.parseInt(p[3], 16);
        }
    }

    public static void draw(DrawContext ctx, int x, int y) {
        if (ChunkFinderConfig.INSTANCE.logoPyramid) {
            ctx.drawTexture(RenderLayer::getGuiTextured, PYRAMIDS, x, y, 0f, 0f, SIZE, SIZE, 128, 128, 128, 128);
            return;
        }
        if (runs == null) parse();
        for (int[] r : runs) {
            ctx.fill(x + r[0], y + r[1], x + r[0] + r[2], y + r[1] + 1, r[3]);
        }
    }
}
