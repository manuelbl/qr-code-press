/*
 * QR Code Press
 *
 * Copyright (c) Manuel Bleichenbacher (MIT License)
 * https://github.com/manuelbl/qr-code-press
 */

package net.codecrete.qrcodepress;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Traces the outline of the dark modules of a QR code as closed polygons.
 * <p>
 * A boundary edge is a side of a dark module whose neighbour is light or outside the QR code. Every
 * boundary edge is directed so that the dark module lies to its <i>right</i>: the top side of a
 * dark module points east, the right side south, the bottom side west and the left side north. Under
 * that orientation, each directed edge has exactly one successor, so the boundary edges partition
 * into closed loops &mdash; and a loop around a group of dark modules comes out clockwise, a loop
 * around a hole counterclockwise, without either case being detected.
 * </p>
 * <p>
 * The walk keeps the dark module on its right. Arriving at a corner point, the two modules ahead
 * decide the direction: a light module ahead-right turns the walk right, a dark module ahead-left
 * turns it left, otherwise it continues straight. The right turn is the whole connectivity rule of
 * the outline: at a corner where two dark modules touch only diagonally, turning right hugs the
 * module the walk came along, so diagonal neighbours stay in separate loops &mdash; groups are
 * connected horizontally and vertically only. Seen from the light side, the same right turn merges
 * two diagonally touching light areas into one hole, the usual duality of 4-connected foreground
 * and 8-connected background. Such a loop passes through the shared corner twice.
 * </p>
 * <p>
 * A vertex is recorded only where the direction changes, so collinear edges collapse and the
 * horizontal and vertical edges of a loop strictly alternate. Every loop contains at least one
 * east edge (the topmost run of a group, or the underside of a hole), so scanning for dark modules
 * with a light module above finds every loop, and marking the east edges walked makes the scan
 * skip loops already traced. Each loop is rotated to start at its topmost, then leftmost vertex,
 * and the loops are sorted by that start vertex in reading order.
 * </p>
 */
final class OutlineBuilder {

    /** The x steps of the directions east, south, west and north. */
    private static final int[] STEP_X = { 1, 0, -1, 0 };

    /** The y steps of the directions east, south, west and north. */
    private static final int[] STEP_Y = { 0, 1, 0, -1 };

    private static final int EAST = 0;
    private static final int SOUTH = 1;
    private static final int WEST = 2;

    private OutlineBuilder() {
        // non-instantiable
    }

    /**
     * Traces the outline of the dark modules of the specified matrix.
     *
     * @param modules the modules; not modified
     * @return the closed loops, ordered by their start vertex in reading order
     */
    static List<QrPolygon> build(BitMatrix modules) {
        var size = modules.size();

        // whether the east edge along the top of module (x, y) has been walked
        var walkedEastEdges = new BitMatrix(size);

        var polygons = new ArrayList<QrPolygon>();
        for (var y = 0; y < size; y += 1) {
            for (var x = 0; x < size; x += 1) {
                if (modules.get(x, y) && !isDark(modules, x, y - 1) && !walkedEastEdges.get(x, y)) {
                    polygons.add(traceLoop(modules, walkedEastEdges, x, y));
                }
            }
        }

        polygons.sort(Comparator.comparing(polygon -> polygon.vertices().get(0),
                Comparator.comparingInt(QrPoint::y).thenComparingInt(QrPoint::x)));
        return polygons;
    }

    /**
     * Walks the loop containing the east edge that starts at the corner point (x, y), and marks the
     * east edges it walks.
     */
    private static QrPolygon traceLoop(BitMatrix modules, BitMatrix walkedEastEdges, int x, int y) {
        var vertices = new ArrayList<QrPoint>();

        var vx = x;
        var vy = y;
        var direction = EAST;
        while (true) {
            if (direction == EAST) {
                walkedEastEdges.set(vx, vy, true);
            }
            vx += STEP_X[direction];
            vy += STEP_Y[direction];

            var turned = turn(modules, vx, vy, direction);
            if (turned != direction) {
                vertices.add(new QrPoint(vx, vy));
                direction = turned;
            }

            // The loop is closed when the walk is about to repeat the edge it started on. The
            // start vertex alone is not enough: a loop pinched at the start vertex passes through
            // it twice, in different directions.
            if (vx == x && vy == y && direction == EAST) {
                break;
            }
        }

        return new QrPolygon(rotateToTopLeft(vertices));
    }

    /**
     * Decides the direction in which the walk leaves the corner point (x, y), arrived at in the
     * specified direction: a light module ahead-right turns it right, a dark module ahead-left
     * turns it left, otherwise it continues straight.
     */
    private static int turn(BitMatrix modules, int x, int y, int direction) {
        // Of the four modules around the corner point, the one ahead-right and the one ahead-left.
        // Heading east they are the ones below and above the continuing edge; the other directions
        // follow by rotation.
        var aheadRight = switch (direction) {
            case EAST -> isDark(modules, x, y);
            case SOUTH -> isDark(modules, x - 1, y);
            case WEST -> isDark(modules, x - 1, y - 1);
            default -> isDark(modules, x, y - 1); // north
        };
        var aheadLeft = switch (direction) {
            case EAST -> isDark(modules, x, y - 1);
            case SOUTH -> isDark(modules, x, y);
            case WEST -> isDark(modules, x - 1, y);
            default -> isDark(modules, x - 1, y - 1); // north
        };

        if (!aheadRight) {
            return (direction + 1) % 4;
        }
        return aheadLeft ? (direction + 3) % 4 : direction;
    }

    /**
     * Returns the colour of the module at the specified coordinates, with everything outside the
     * QR code light.
     */
    private static boolean isDark(BitMatrix modules, int x, int y) {
        var size = modules.size();
        return 0 <= x && x < size && 0 <= y && y < size && modules.get(x, y);
    }

    /**
     * Rotates the vertices so the loop starts at its topmost, then leftmost vertex.
     * <p>
     * That vertex is a corner of every rectilinear loop, so it is in the list: the interior points
     * of a horizontal run share their y with an endpoint further left, those of a vertical run lie
     * below an endpoint.
     * </p>
     */
    private static List<QrPoint> rotateToTopLeft(List<QrPoint> vertices) {
        var start = 0;
        for (var i = 1; i < vertices.size(); i += 1) {
            var vertex = vertices.get(i);
            var best = vertices.get(start);
            if (vertex.y() < best.y() || (vertex.y() == best.y() && vertex.x() < best.x())) {
                start = i;
            }
        }

        if (start == 0) {
            return vertices;
        }
        var rotated = new ArrayList<QrPoint>(vertices.size());
        rotated.addAll(vertices.subList(start, vertices.size()));
        rotated.addAll(vertices.subList(0, start));
        return rotated;
    }
}
