package com.legumlex.client.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser

/* ============================================================================
 *  A workspace's mark, as Mylex draws it (mylex/views/client/partials/
 *  workspace_gate.php): the Supreme Court of India for Litigation, a clipboard
 *  of tasks for Projects, a document under the firm's stamp for Registry.
 *
 *  Generated from the portal's own SVG (viewBox 116 × 132), so the two stay
 *  one drawing: `ink` is the SVG's currentColor, `surface` its --gate-surface
 *  (the card behind, filling shapes that must hide what is under them).
 * ========================================================================== */

enum class Workspace { Litigation, Projects, Registry }

private data class Rot(val deg: Float, val x: Float, val y: Float)

private class GateOp(
    val d: String,
    val fill: Int,          // 0 none, 1 ink, 2 surface
    val stroke: Int,        // 0 none, 1 ink
    val width: Float,
    val round: Boolean,
    val alpha: Float,
    val rot: List<Rot> = emptyList(),
)

@Composable
fun GateArt(workspace: Workspace, ink: Color, surface: Color, modifier: Modifier = Modifier) {
    val ops = when (workspace) {
        Workspace.Litigation -> LitigationOps
        Workspace.Projects   -> ProjectsOps
        Workspace.Registry   -> RegistryOps
    }
    val paths: List<Path> = remember(workspace) { ops.map { PathParser().parsePathString(it.d).toPath() } }
    Canvas(modifier) {
        scale(size.width / 116f, size.height / 132f, pivot = androidx.compose.ui.geometry.Offset.Zero) {
            ops.forEachIndexed { i, op ->
                val draw = {
                    if (op.fill != 0) drawPath(paths[i], if (op.fill == 1) ink else surface, alpha = op.alpha, style = Fill)
                    if (op.stroke != 0) drawPath(
                        paths[i], ink, alpha = op.alpha,
                        style = Stroke(
                            width = op.width,
                            cap = if (op.round) StrokeCap.Round else StrokeCap.Butt,
                            join = if (op.round) StrokeJoin.Round else StrokeJoin.Miter,
                        ),
                    )
                }
                op.rot.fold(draw) { inner, r -> { rotate(r.deg, androidx.compose.ui.geometry.Offset(r.x, r.y)) { inner() } } }()
            }
        }
    }
}

private val LitigationOps = listOf(
    GateOp("M48.0 15.0a10.0 10.0 0 1 0 20.0 0a10.0 10.0 0 1 0 -20.0 0Z", fill = 0, stroke = 1, width = 1.75f, round = false, alpha = 1.0f),
    GateOp("M60.60 15.00L67.00 15.00M60.51 15.67L66.69 17.33M60.25 16.30L65.79 19.50M59.84 16.84L64.36 21.36M59.30 17.25L62.50 22.79M58.67 17.51L60.33 23.69M58.00 17.60L58.00 24.00M57.33 17.51L55.67 23.69M56.70 17.25L53.50 22.79M56.16 16.84L51.64 21.36M55.75 16.30L50.21 19.50M55.49 15.67L49.31 17.33M55.40 15.00L49.00 15.00M55.49 14.33L49.31 12.67M55.75 13.70L50.21 10.50M56.16 13.16L51.64 8.64M56.70 12.75L53.50 7.21M57.33 12.49L55.67 6.31M58.00 12.40L58.00 6.00M58.67 12.49L60.33 6.31M59.30 12.75L62.50 7.21M59.84 13.16L64.36 8.64M60.25 13.70L65.79 10.50M60.51 14.33L66.69 12.67", fill = 0, stroke = 1, width = 0.8f, round = true, alpha = 1.0f),
    GateOp("M55.8 15.0a2.2 2.2 0 1 0 4.4 0a2.2 2.2 0 1 0 -4.4 0Z", fill = 1, stroke = 0, width = 1.0f, round = false, alpha = 1.0f),
    GateOp("M58 126C44 121 22 120 4 124L14 101C30 98 46 100 58 106M58 126C72 121 94 120 112 124L102 101C86 98 70 100 58 106M58 121C44 116 26 115 10 118M58 121C72 116 90 115 106 118M58 106V126", fill = 0, stroke = 1, width = 1.5f, round = true, alpha = 1.0f),
    GateOp("M55.2 27.5h5.6a1.2 1.2 0 0 1 1.2 1.2v0.0a1.2 1.2 0 0 1 -1.2 1.2h-5.6a1.2 1.2 0 0 1 -1.2 -1.2v-0.0a1.2 1.2 0 0 1 1.2 -1.2Z", fill = 1, stroke = 0, width = 1.0f, round = false, alpha = 1.0f),
    GateOp("M51.1 31.0h13.8a1.1 1.1 0 0 1 1.1 1.1v0.0a1.1 1.1 0 0 1 -1.1 1.1h-13.8a1.1 1.1 0 0 1 -1.1 -1.1v-0.0a1.1 1.1 0 0 1 1.1 -1.1Z", fill = 1, stroke = 0, width = 1.0f, round = false, alpha = 1.0f),
    GateOp("M53.0 34.0h10.0v3.2h-10.0Z", fill = 1, stroke = 0, width = 1.0f, round = false, alpha = 1.0f),
    GateOp("M34 61A24 24 0 0 1 82 61Z", fill = 1, stroke = 0, width = 1.0f, round = false, alpha = 1.0f),
    GateOp("M26.0 62.0h64.0v4.5h-64.0Z", fill = 1, stroke = 0, width = 1.0f, round = false, alpha = 1.0f),
    GateOp("M29.0 67.5h58.0v1.8h-58.0Z", fill = 1, stroke = 0, width = 1.0f, round = false, alpha = 1.0f),
    GateOp("M30.5 70.5h5.5v20.5h-5.5Z", fill = 1, stroke = 0, width = 1.0f, round = false, alpha = 1.0f),
    GateOp("M40.7 70.5h5.5v20.5h-5.5Z", fill = 1, stroke = 0, width = 1.0f, round = false, alpha = 1.0f),
    GateOp("M50.9 70.5h5.5v20.5h-5.5Z", fill = 1, stroke = 0, width = 1.0f, round = false, alpha = 1.0f),
    GateOp("M61.1 70.5h5.5v20.5h-5.5Z", fill = 1, stroke = 0, width = 1.0f, round = false, alpha = 1.0f),
    GateOp("M71.3 70.5h5.5v20.5h-5.5Z", fill = 1, stroke = 0, width = 1.0f, round = false, alpha = 1.0f),
    GateOp("M81.5 70.5h5.5v20.5h-5.5Z", fill = 1, stroke = 0, width = 1.0f, round = false, alpha = 1.0f),
    GateOp("M36.0 92.0h44.0v3.5h-44.0Z", fill = 1, stroke = 0, width = 1.0f, round = false, alpha = 1.0f),
    GateOp("M40.0 96.5h36.0v5.5h-36.0Z", fill = 1, stroke = 0, width = 1.0f, round = false, alpha = 1.0f),
    GateOp("M22.3 67.5h3.4a0.8 0.8 0 0 1 0.8 0.8v0.0a0.8 0.8 0 0 1 -0.8 0.8h-3.4a0.8 0.8 0 0 1 -0.8 -0.8v-0.0a0.8 0.8 0 0 1 0.8 -0.8Z", fill = 1, stroke = 0, width = 1.0f, round = false, alpha = 1.0f),
    GateOp("M23.0 69.0h2.0v4.0h-2.0Z", fill = 1, stroke = 0, width = 1.0f, round = false, alpha = 1.0f),
    GateOp("M16 81A8 8 0 0 1 32 81Z", fill = 1, stroke = 0, width = 1.0f, round = false, alpha = 1.0f),
    GateOp("M14.0 82.0h20.0v3.0h-20.0Z", fill = 1, stroke = 0, width = 1.0f, round = false, alpha = 1.0f),
    GateOp("M16.0 86.0h4.5v13.5h-4.5Z", fill = 1, stroke = 0, width = 1.0f, round = false, alpha = 1.0f),
    GateOp("M27.5 86.0h4.5v13.5h-4.5Z", fill = 1, stroke = 0, width = 1.0f, round = false, alpha = 1.0f),
    GateOp("M90.3 67.5h3.4a0.8 0.8 0 0 1 0.8 0.8v0.0a0.8 0.8 0 0 1 -0.8 0.8h-3.4a0.8 0.8 0 0 1 -0.8 -0.8v-0.0a0.8 0.8 0 0 1 0.8 -0.8Z", fill = 1, stroke = 0, width = 1.0f, round = false, alpha = 1.0f),
    GateOp("M91.0 69.0h2.0v4.0h-2.0Z", fill = 1, stroke = 0, width = 1.0f, round = false, alpha = 1.0f),
    GateOp("M84 81A8 8 0 0 1 100 81Z", fill = 1, stroke = 0, width = 1.0f, round = false, alpha = 1.0f),
    GateOp("M82.0 82.0h20.0v3.0h-20.0Z", fill = 1, stroke = 0, width = 1.0f, round = false, alpha = 1.0f),
    GateOp("M84.0 86.0h4.5v13.5h-4.5Z", fill = 1, stroke = 0, width = 1.0f, round = false, alpha = 1.0f),
    GateOp("M95.5 86.0h4.5v13.5h-4.5Z", fill = 1, stroke = 0, width = 1.0f, round = false, alpha = 1.0f),
)
private val ProjectsOps = listOf(
    GateOp("M20.0 16.0h72.0a4.0 4.0 0 0 1 4.0 4.0v100.0a4.0 4.0 0 0 1 -4.0 4.0h-72.0a4.0 4.0 0 0 1 -4.0 -4.0v-100.0a4.0 4.0 0 0 1 4.0 -4.0Z", fill = 2, stroke = 1, width = 4.0f, round = false, alpha = 1.0f),
    GateOp("M43.0 7.0h26.0a3.0 3.0 0 0 1 3.0 3.0v11.0a3.0 3.0 0 0 1 -3.0 3.0h-26.0a3.0 3.0 0 0 1 -3.0 -3.0v-11.0a3.0 3.0 0 0 1 3.0 -3.0Z", fill = 1, stroke = 0, width = 1.0f, round = false, alpha = 1.0f),
    GateOp("M53.5 11.0h5.0a2.5 2.5 0 0 1 2.5 2.5v0.0a2.5 2.5 0 0 1 -2.5 2.5h-5.0a2.5 2.5 0 0 1 -2.5 -2.5v-0.0a2.5 2.5 0 0 1 2.5 -2.5Z", fill = 2, stroke = 0, width = 1.0f, round = false, alpha = 1.0f),
    GateOp("M30.0 40.0h11.0a2.0 2.0 0 0 1 2.0 2.0v11.0a2.0 2.0 0 0 1 -2.0 2.0h-11.0a2.0 2.0 0 0 1 -2.0 -2.0v-11.0a2.0 2.0 0 0 1 2.0 -2.0Z", fill = 0, stroke = 1, width = 3.5f, round = false, alpha = 1.0f),
    GateOp("M30.0 66.0h11.0a2.0 2.0 0 0 1 2.0 2.0v11.0a2.0 2.0 0 0 1 -2.0 2.0h-11.0a2.0 2.0 0 0 1 -2.0 -2.0v-11.0a2.0 2.0 0 0 1 2.0 -2.0Z", fill = 0, stroke = 1, width = 3.5f, round = false, alpha = 1.0f),
    GateOp("M30.0 92.0h11.0a2.0 2.0 0 0 1 2.0 2.0v11.0a2.0 2.0 0 0 1 -2.0 2.0h-11.0a2.0 2.0 0 0 1 -2.0 -2.0v-11.0a2.0 2.0 0 0 1 2.0 -2.0Z", fill = 0, stroke = 1, width = 3.5f, round = false, alpha = 1.0f),
    GateOp("M52 47.5h32M52 73.5h32M52 99.5h24", fill = 0, stroke = 1, width = 4.5f, round = false, alpha = 1.0f),
    GateOp("M31 47.5l4 4.5 9-11", fill = 0, stroke = 1, width = 4.0f, round = true, alpha = 1.0f),
    GateOp("M31 73.5l4 4.5 9-11", fill = 0, stroke = 1, width = 4.0f, round = true, alpha = 1.0f),
    GateOp("M31 99.5l4 4.5 9-11", fill = 0, stroke = 1, width = 4.0f, round = true, alpha = 1.0f),
)
private val RegistryOps = listOf(
    GateOp("M8.0 8.0h68.0v102.0h-68.0Z", fill = 2, stroke = 1, width = 4.0f, round = false, alpha = 1.0f),
    GateOp("M20 26h42M20 38h42M20 50h42M20 62h42M20 74h32M20 86h26", fill = 0, stroke = 1, width = 4.5f, round = false, alpha = 1.0f),
    GateOp("M60.0 97.0a20.0 20.0 0 1 0 40.0 0a20.0 20.0 0 1 0 -40.0 0Z", fill = 0, stroke = 1, width = 2.0f, round = false, alpha = 0.45f),
    GateOp("M80.00 90.50L81.59 94.82L86.18 94.99L82.57 97.83L83.82 102.26L80.00 99.70L76.18 102.26L77.43 97.83L73.82 94.99L78.41 94.82Z", fill = 1, stroke = 0, width = 1.0f, round = false, alpha = 0.45f),
    GateOp("M71.0 46.0a9.0 9.5 0 1 0 18.0 0a9.0 9.5 0 1 0 -18.0 0Z", fill = 1, stroke = 0, width = 1.0f, round = false, alpha = 1.0f, rot = listOf(Rot(32f, 80f, 97f))),
    GateOp("M74.5 42.0a2.0 3.2 0 1 0 4.0 0a2.0 3.2 0 1 0 -4.0 0Z", fill = 2, stroke = 0, width = 1.0f, round = false, alpha = 1.0f, rot = listOf(Rot(32f, 80f, 97f), Rot(-20f, 76.5f, 42f))),
    GateOp("M75.0 52.0h10.0v15.0h-10.0Z", fill = 1, stroke = 0, width = 1.0f, round = false, alpha = 1.0f, rot = listOf(Rot(32f, 80f, 97f))),
    GateOp("M64.0 66.0h32.0a2.0 2.0 0 0 1 2.0 2.0v5.0a2.0 2.0 0 0 1 -2.0 2.0h-32.0a2.0 2.0 0 0 1 -2.0 -2.0v-5.0a2.0 2.0 0 0 1 2.0 -2.0Z", fill = 1, stroke = 0, width = 1.0f, round = false, alpha = 1.0f, rot = listOf(Rot(32f, 80f, 97f))),
    GateOp("M59.0 97.0a21.0 21.0 0 1 0 42.0 0a21.0 21.0 0 1 0 -42.0 0Z", fill = 2, stroke = 1, width = 4.5f, round = false, alpha = 1.0f),
    GateOp("M65.5 97.0a14.5 14.5 0 1 0 29.0 0a14.5 14.5 0 1 0 -29.0 0Z", fill = 0, stroke = 1, width = 1.5f, round = false, alpha = 1.0f),
    GateOp("M80.00 90.00L81.70 94.65L86.66 94.84L82.76 97.90L84.11 102.66L80.00 99.90L75.89 102.66L77.24 97.90L73.34 94.84L78.30 94.65Z", fill = 1, stroke = 0, width = 1.0f, round = false, alpha = 1.0f),
)
