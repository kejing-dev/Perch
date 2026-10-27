package io.embodit.perch.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Perch design tokens ("morning desk" palette from design/design-doc.md §3).
 *
 * Semantic roles that map onto a PicoTheme [com.pico.spatial.ui.design.ColorScheme] slot are
 * injected in [PerchTheme]; the rest are brand tokens used verbatim on inner surfaces only. The
 * window root always keeps the system Material.Regular glass.
 */
object PerchColors {
    // ---- ColorScheme role overrides -------------------------------------------------------
    /** 9 a.m. light: the one active accent (Start, branch fill, focus chip). */
    val Ochre = Color(0xFFD89B3C) // design-style: fixed-figma-color design-doc §3 interaction
    /** Moss on the sill: landed / done marks and the day tally. */
    val Moss = Color(0xFF6F8B6A) // design-style: fixed-figma-color design-doc §3 passable
    /** Robin breast: reserved for the eye-rest break (Landing / Resting). */
    val Robin = Color(0xFFC4623F) // design-style: fixed-figma-color design-doc §3 alert
    /** Deep robin for destructive actions (delete a task). */
    val Error = Color(0xFFB3402B) // design-style: fixed-figma-color design-doc §3 error

    // ---- Brand tokens (no ColorScheme role) -----------------------------------------------
    /** Desk paper: the Perch card surface, opaque so the clock stays legible over passthrough. */
    val Paper = Color(0xFFF4ECDC) // design-style: fixed-figma-color design-doc §3 paper
    /** Paper edge: 1 dp card outline. */
    val PaperEdge = Color(0xFFE3D8C2) // design-style: fixed-figma-color design-doc §3 paper edge
    /** Blush paper: the card surface while the eye-rest break is due or running. */
    val PaperBlush = Color(0xFFF6E0D6) // design-style: fixed-figma-color design-doc §3 blush paper
    /** Pencil graphite: ink on paper (clock digits, perched title). */
    val Graphite = Color(0xFF2A2723) // design-style: fixed-figma-color design-doc §3 graphite
    /** Softer graphite for meta text on paper. */
    val GraphiteSoft = Color(0xFF6B6560) // design-style: fixed-figma-color design-doc §3 graphite soft
    /** Faint ochre for the empty branch outline and the ochre track behind progress. */
    val OchreTrack = Color(0x33D89B3C) // design-style: fixed-figma-color design-doc §3 ochre track
    /** Faint robin track behind the draining break branch. */
    val RobinTrack = Color(0x33C4623F) // design-style: fixed-figma-color design-doc §3 robin track
    /** Baseline and hour ticks of the day-wire time axis (graphite at 25 %). */
    val WireTrack = Color(0x402A2723) // design-style: fixed-figma-color design-doc §3 wire track
}
