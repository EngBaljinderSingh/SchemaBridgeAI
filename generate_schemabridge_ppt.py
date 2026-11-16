"""Generate executive 'SchemaBridge AI Aviator.pptx' positioned as Agentic Enterprise Integration Accelerator."""

from __future__ import annotations

from pathlib import Path
from pptx import Presentation
from pptx.dml.color import RGBColor
from pptx.enum.shapes import MSO_SHAPE
from pptx.enum.text import MSO_ANCHOR, PP_ALIGN
from pptx.util import Inches, Pt

ROOT = Path(__file__).resolve().parent
BASE_PPTX = ROOT / "SchemaBridge AI Aviator.pptx"
OUTPUT_PPTX = ROOT / "SchemaBridge AI Aviator.pptx"

UPLOAD_DIR = Path(r"C:/Users/baljinders/.gemini/antigravity/brain/00302878-1269-4261-80d4-f9ff35bac9f3/.user_uploaded")
IMG_GATEWAY_6CASES = UPLOAD_DIR / "media_1794822702836_de1f4239.jpg"
IMG_MAPPING_WORKSPACE = UPLOAD_DIR / "media_1794822766024_0c0ca0ae.jpg"
IMG_TRANSFORM_LAB = UPLOAD_DIR / "media_1794822817232_afa558ac.jpg"

# OpenText Theme Palette
OT_NAVY = RGBColor(0x00, 0x00, 0x8A)       # #00008A
OT_BLUE = RGBColor(0x19, 0x68, 0xFF)       # #1968FF
OT_DARK_NAVY = RGBColor(0x00, 0x1C, 0x50)  # #001C50
OT_SLATE = RGBColor(0x66, 0x67, 0x7B)      # #66677B
OT_LIGHT_GRAY = RGBColor(0xE7, 0xE9, 0xEC) # #E7E9EC
OT_SOFT_BG = RGBColor(0xF4, 0xF6, 0xFA)    # subtle card fill
OT_GOLD = RGBColor(0xE1, 0xBB, 0x35)       # #E1BB35
OT_TEAL = RGBColor(0x0D, 0x94, 0x88)       # #0D9488
OT_CHARCOAL = RGBColor(0x37, 0x38, 0x48)   # #373848
OT_RED = RGBColor(0x99, 0x1B, 0x1B)        # #991B1B
OT_WHITE = RGBColor(0xFF, 0xFF, 0xFF)


def update_master_footer_year(prs: Presentation, old_year: str = "2025", new_year: str = "2026") -> None:
    for master in prs.slide_masters:
        for shape in master.shapes:
            if shape.has_text_frame:
                for paragraph in shape.text_frame.paragraphs:
                    for run in paragraph.runs:
                        if old_year in run.text:
                            run.text = run.text.replace(old_year, new_year)
        for layout in master.slide_layouts:
            for shape in layout.shapes:
                if shape.has_text_frame:
                    for paragraph in shape.text_frame.paragraphs:
                        for run in paragraph.runs:
                            if old_year in run.text:
                                run.text = run.text.replace(old_year, new_year)


def set_title(slide, text: str) -> None:
    for shape in slide.placeholders:
        if shape.placeholder_format.idx == 0:
            shape.text = text
            return


def remove_placeholder(slide, idx: int) -> None:
    for shape in list(slide.placeholders):
        if shape.placeholder_format.idx == idx:
            sp = shape._element
            sp.getparent().remove(sp)


def set_notes(slide, notes_text: str) -> None:
    notes_slide = slide.notes_slide
    tf = notes_slide.notes_text_frame
    tf.text = notes_text.strip()


def sanitize_backup_slide(slide, title_prefix: str = "Backup: ") -> None:
    replacements = [
        ("GDPR, HIPAA, PCI safe", "supports enterprise data-privacy and regulatory requirements"),
        ("GDPR / HIPAA / PCI", "enterprise data-privacy and regulatory requirements"),
        ("GDPR, HIPAA, PCI", "enterprise data-privacy and regulatory requirements"),
        ("(Patent-Pending)", ""),
        ("Patent-Pending", ""),
        ("patent-pending", ""),
        ("Patent Disclosure", "Architecture Overview"),
        ("patent disclosure", "architecture overview"),
        ("Patent", "IP"),
        ("patent", "IP"),
        ("Zero hallucination guarantee", "Zero-guessing policy in production (deterministic runtime)"),
        ("zero hallucination guarantee", "zero-guessing policy in production (deterministic runtime)"),
        ("100% deterministic", "deterministic"),
        ("100% Privacy", "Privacy-by-Architecture (Zero Runtime PII Exposure to AI)"),
        ("100% Data Privacy", "Privacy-by-Architecture (Zero Runtime PII Exposure to AI)"),
        ("100% data privacy", "privacy-by-architecture (zero runtime PII exposure to AI)"),
        ("baljinders@opentext.com", "designated engineering approver"),
    ]

    def walk_shapes(shapes):
        for sh in shapes:
            if sh.has_text_frame:
                for p in sh.text_frame.paragraphs:
                    for r in p.runs:
                        txt = r.text
                        for old, new in replacements:
                            if old in txt:
                                txt = txt.replace(old, new)
                        r.text = txt
            if sh.has_table:
                for row in sh.table.rows:
                    for cell in row.cells:
                        for p in cell.text_frame.paragraphs:
                            for r in p.runs:
                                txt = r.text
                                for old, new in replacements:
                                    if old in txt:
                                        txt = txt.replace(old, new)
                                r.text = txt
            if hasattr(sh, "shapes"):
                walk_shapes(sh.shapes)

    walk_shapes(slide.shapes)

    for sh in slide.placeholders:
        if sh.placeholder_format.idx == 0 and sh.has_text_frame:
            t = sh.text_frame.text.strip()
            if t and not t.startswith(title_prefix):
                sh.text = f"{title_prefix}{t}"
            break


def reorder_and_prune_slides(prs: Presentation, keep_indices_in_order: list[int]) -> None:
    sldIdLst = prs.slides._sldIdLst
    all_els = list(sldIdLst)
    keep_set = set(keep_indices_in_order)

    for idx, el in enumerate(all_els):
        if idx not in keep_set:
            prs.part.drop_rel(el.rId)
            sldIdLst.remove(el)

    ordered_els = [all_els[idx] for idx in keep_indices_in_order]
    for el in ordered_els:
        sldIdLst.remove(el)
    for el in ordered_els:
        sldIdLst.append(el)


def add_card(
    slide,
    left: float,
    top: float,
    width: float,
    height: float,
    title: str,
    bullets: list[str],
    header_color: RGBColor = OT_NAVY,
    fill_color: RGBColor = OT_SOFT_BG,
    border_color: RGBColor = OT_LIGHT_GRAY,
    title_size: float = 11.8,
    body_size: float = 9.2,
) -> None:
    body_shape = slide.shapes.add_shape(
        MSO_SHAPE.ROUNDED_RECTANGLE,
        Inches(left),
        Inches(top),
        Inches(width),
        Inches(height),
    )
    body_shape.adjustments[0] = 0.03
    body_shape.fill.solid()
    body_shape.fill.fore_color.rgb = fill_color
    body_shape.line.color.rgb = border_color
    body_shape.line.width = Pt(1)

    hdr_h = 0.38
    hdr_shape = slide.shapes.add_shape(
        MSO_SHAPE.ROUND_2_SAME_RECTANGLE,
        Inches(left),
        Inches(top),
        Inches(width),
        Inches(hdr_h),
    )
    hdr_shape.adjustments[0] = 0.03
    hdr_shape.fill.solid()
    hdr_shape.fill.fore_color.rgb = header_color
    hdr_shape.line.fill.background()

    tf_h = hdr_shape.text_frame
    tf_h.word_wrap = True
    tf_h.margin_left = Inches(0.14)
    tf_h.margin_right = Inches(0.12)
    tf_h.vertical_anchor = MSO_ANCHOR.MIDDLE
    p_h = tf_h.paragraphs[0]
    p_h.text = title
    p_h.font.name = "Arial"
    p_h.font.size = Pt(title_size)
    p_h.font.bold = True
    p_h.font.color.rgb = OT_WHITE

    tb = slide.shapes.add_textbox(
        Inches(left + 0.12),
        Inches(top + hdr_h + 0.05),
        Inches(width - 0.24),
        Inches(height - hdr_h - 0.09),
    )
    tf = tb.text_frame
    tf.word_wrap = True
    tf.margin_left = Inches(0.04)
    tf.margin_right = Inches(0.04)
    tf.margin_top = Inches(0.02)

    for i, item in enumerate(bullets):
        p = tf.paragraphs[0] if i == 0 else tf.add_paragraph()
        p.space_after = Pt(3.5)
        if ":" in item and not item.startswith("http"):
            prefix, rest = item.split(":", 1)
            r1 = p.add_run()
            r1.text = f"• {prefix}:"
            r1.font.name = "Arial"
            r1.font.size = Pt(body_size)
            r1.font.bold = True
            r1.font.color.rgb = OT_DARK_NAVY

            r2 = p.add_run()
            r2.text = rest
            r2.font.name = "Arial"
            r2.font.size = Pt(body_size)
            r2.font.color.rgb = OT_CHARCOAL
        else:
            r = p.add_run()
            r.text = f"• {item}"
            r.font.name = "Arial"
            r.font.size = Pt(body_size)
            r.font.color.rgb = OT_CHARCOAL


def add_banner(
    slide,
    left: float,
    top: float,
    width: float,
    height: float,
    text: str,
    bg_color: RGBColor = OT_DARK_NAVY,
    fg_color: RGBColor = OT_WHITE,
    font_size: float = 10.4,
) -> None:
    box = slide.shapes.add_shape(
        MSO_SHAPE.ROUNDED_RECTANGLE,
        Inches(left),
        Inches(top),
        Inches(width),
        Inches(height),
    )
    box.adjustments[0] = 0.08
    box.fill.solid()
    box.fill.fore_color.rgb = bg_color
    box.line.fill.background()
    tf = box.text_frame
    tf.word_wrap = True
    tf.vertical_anchor = MSO_ANCHOR.MIDDLE
    tf.margin_left = Inches(0.16)
    tf.margin_right = Inches(0.16)
    p = tf.paragraphs[0]
    p.text = text
    p.font.name = "Arial"
    p.font.size = Pt(font_size)
    p.font.bold = True
    p.font.color.rgb = fg_color


def add_pill_box(
    slide,
    left: float,
    top: float,
    width: float,
    height: float,
    title: str,
    subtitle: str = "",
    fill_color: RGBColor = OT_BLUE,
    text_color: RGBColor = OT_WHITE,
    title_size: float = 9.4,
    sub_size: float = 8.0,
) -> None:
    shape = slide.shapes.add_shape(
        MSO_SHAPE.ROUNDED_RECTANGLE,
        Inches(left),
        Inches(top),
        Inches(width),
        Inches(height),
    )
    shape.adjustments[0] = 0.1
    shape.fill.solid()
    shape.fill.fore_color.rgb = fill_color
    shape.line.fill.background()
    tf = shape.text_frame
    tf.word_wrap = True
    tf.vertical_anchor = MSO_ANCHOR.MIDDLE
    tf.margin_left = Inches(0.06)
    tf.margin_right = Inches(0.06)

    p = tf.paragraphs[0]
    p.alignment = PP_ALIGN.CENTER
    p.text = title
    p.font.name = "Arial"
    p.font.size = Pt(title_size)
    p.font.bold = True
    p.font.color.rgb = text_color

    if subtitle:
        p2 = tf.add_paragraph()
        p2.alignment = PP_ALIGN.CENTER
        p2.space_before = Pt(1.5)
        p2.text = subtitle
        p2.font.name = "Arial"
        p2.font.size = Pt(sub_size)
        p2.font.color.rgb = text_color


def add_framed_screenshot(
    slide,
    image_path: Path,
    left: float,
    top: float,
    frame_w: float,
    frame_h: float,
    border_color: RGBColor = OT_BLUE,
) -> None:
    if not image_path.is_file():
        return
    frame = slide.shapes.add_shape(
        MSO_SHAPE.ROUNDED_RECTANGLE,
        Inches(left),
        Inches(top),
        Inches(frame_w),
        Inches(frame_h),
    )
    frame.adjustments[0] = 0.02
    frame.fill.solid()
    frame.fill.fore_color.rgb = OT_WHITE
    frame.line.color.rgb = border_color
    frame.line.width = Pt(1.5)

    slide.shapes.add_picture(
        str(image_path),
        Inches(left + 0.07),
        Inches(top + 0.07),
        width=Inches(frame_w - 0.14),
    )


def add_comparison_table(slide, left: float, top: float, width: float, height: float) -> None:
    rows_data = [
        ("Dimension", "Traditional Integration", "SchemaBridge AI"),
        ("Time to Go-Live", "Weeks of custom analysis, coding, testing & deployment", "AI-assisted onboarding (~10 min demonstrated vs. 3–4 wks estimated)"),
        ("New Integration Cost", "Build, deploy, and maintain another custom adapter/service", "Configure and govern a versioned integration contract"),
        ("Runtime", "Custom middleware / potential runtime processing overhead", "Deterministic Java, demonstrated <5 ms transformation"),
        ("API Change", "Analyze code -> modify -> rebuild -> regression test -> redeploy", "Detect contract drift -> identify impacted mappings -> review -> republish"),
        ("Data Privacy", "Runtime AI approaches may expose live payloads to LLMs", "Zero production LLM calls (no live customer payload sent to AI)"),
        ("Debugging", "Trace custom services, transformation code, and scattered logs", "Centralized validation, correlation IDs & transformation visibility"),
        ("Maintenance", "Frameworks, libraries & service lifecycle per custom adapter", "Shared gateway + versioned integration contracts"),
    ]

    table_shape = slide.shapes.add_table(len(rows_data), 3, Inches(left), Inches(top), Inches(width), Inches(height))
    table = table_shape.table
    table.columns[0].width = Inches(2.10)
    table.columns[1].width = Inches(4.85)
    table.columns[2].width = Inches(5.03)

    for r_idx, (col0, col1, col2) in enumerate(rows_data):
        is_hdr = r_idx == 0
        for c_idx, val in enumerate([col0, col1, col2]):
            cell = table.cell(r_idx, c_idx)
            cell.vertical_anchor = MSO_ANCHOR.MIDDLE
            cell.margin_left = Inches(0.10)
            cell.margin_right = Inches(0.10)
            cell.margin_top = Inches(0.05)
            cell.margin_bottom = Inches(0.05)
            cell.fill.solid()
            if is_hdr:
                cell.fill.fore_color.rgb = OT_NAVY if c_idx < 2 else OT_TEAL
            else:
                cell.fill.fore_color.rgb = OT_SOFT_BG if r_idx % 2 == 1 else OT_WHITE

            tf = cell.text_frame
            tf.word_wrap = True
            p = tf.paragraphs[0]
            p.text = val
            p.font.name = "Arial"
            p.font.size = Pt(10.2 if is_hdr else 9.2)
            p.font.bold = is_hdr or c_idx == 0
            if is_hdr:
                p.font.color.rgb = OT_WHITE
            elif c_idx == 0:
                p.font.color.rgb = OT_DARK_NAVY
            elif c_idx == 2:
                p.font.color.rgb = OT_DARK_NAVY
            else:
                p.font.color.rgb = OT_CHARCOAL


def build_presentation() -> Path:
    prs = Presentation(str(BASE_PPTX))
    update_master_footer_year(prs, "2025", "2026")

    old_count = len(prs.slides)  # 16 slides in SchemaBridge AI Aviatorold.pptx (0..15)
    layouts = prs.slide_layouts
    L_COVER = layouts[0]       # 'Cover'
    L_TITLE_BODY = layouts[3]  # 'Title | Body'
    L_BACK = layouts[28]       # 'Back cover'

    # =========================================================================
    # SLIDE 1: Cover Slide
    # =========================================================================
    s1 = prs.slides.add_slide(L_COVER)
    s1.placeholders[10].text = "SchemaBridge AI"
    s1.placeholders[11].text = "Agentic Enterprise Integration Accelerator"
    s1.placeholders[12].text = (
        "Baljinder Singh  |  Powered by OpenText Aviator ADT  |  Coding & Integration Automation\n"
        "AI Designs the Integration  ·  Deterministic Java Runs It (<5 ms Demonstrated, 0 Live LLM Calls)"
    )
    set_notes(
        s1,
        "Good morning Michael and Marc. Today I am presenting SchemaBridge AI, our Agentic "
        "Enterprise Integration Accelerator powered by OpenText Aviator ADT. The problem we solve "
        "is not just mapping fields—it is the time, cost, operational complexity, maintenance burden, "
        "and risk of integrating enterprise systems and keeping those integrations working as APIs evolve."
    )

    # =========================================================================
    # SLIDE 2: Executive Showcase Summary
    # =========================================================================
    s2 = prs.slides.add_slide(L_TITLE_BODY)
    set_title(s2, "1. Executive Showcase Summary: Enterprise Integration Accelerator")
    remove_placeholder(s2, 1)

    add_card(
        s2, 0.67, 1.42, 5.80, 2.58,
        "Project / Area & Enterprise Challenge",
        [
            "Project / Area: SchemaBridge AI — Agentic Enterprise Integration Accelerator (SE <-> CM3).",
            "Business Challenge: Integrating enterprise systems (and keeping them working as APIs evolve) drives weeks of custom adapter coding, ongoing framework/library maintenance, brittle debugging, and operational risk.",
            "Enabling Technology: Uses schema intelligence and a 5-Level Matcher underneath a governed integration lifecycle.",
        ],
        header_color=OT_RED, title_size=11.6, body_size=9.2,
    )

    add_card(
        s2, 6.85, 1.42, 5.80, 2.58,
        "AI-Assisted Approach (OpenText Aviator ADT)",
        [
            "Agentic Onboarding (Design Time): Ingests API contracts/Swagger (or sample JSON), runs 4 deterministic matching tiers first, and invokes OpenText Aviator ADT only for semantic/ambiguous integration decisions.",
            "Shared Deterministic Gateway (Runtime): Publishes versioned integration contracts executed by pure Java in <5 ms (demonstrated) with 0 production LLM calls.",
        ],
        header_color=OT_NAVY, title_size=11.6, body_size=9.2,
    )

    add_card(
        s2, 0.67, 4.15, 5.80, 2.68,
        "Human Guardrails & Governance",
        [
            "Confidence-Gated Review: High-confidence deterministic matches (>=90%) auto-approve; ambiguous or AI-inferred rules (<90%) require engineer approval before publishing.",
            "Versioned Contracts & Drift Control: Only APPROVED & PUBLISHED versions serve traffic; updated Swagger specs trigger drift checks on impacted mappings.",
            "Observable Safe Failure: Unmapped routes and invalid payloads quarantine cleanly (HTTP 422) with correlation IDs.",
        ],
        header_color=OT_BLUE, title_size=11.6, body_size=9.2,
    )

    add_card(
        s2, 6.85, 4.15, 5.80, 2.68,
        "Impact, Learning & Short Demo Plan",
        [
            "Impact: ~10 min demonstrated SE <-> CM3 onboarding vs. 3–4 weeks estimated traditional build; <5 ms runtime (demonstrated); 0 production LLM calls (no live payload sent to AI).",
            "Learning: Separating AI-assisted contract design from deterministic Java execution delivers LLM agility without runtime latency, data exposure, or custom adapter sprawl.",
            "Demo Story: Onboard CM3 as a new integration for SE -> publish governed contract -> run Write & Reverse Read -> quarantine failure -> inspect drift.",
        ],
        header_color=OT_TEAL, title_size=11.6, body_size=9.2,
    )
    set_notes(
        s2,
        "This executive summary outlines SchemaBridge AI across problem, approach, guardrails, and "
        "impact. Traditional enterprise integrations require weeks of custom adapter development and "
        "ongoing maintenance whenever APIs evolve. SchemaBridge uses OpenText Aviator ADT at design "
        "time to accelerate onboarding from an estimated three to four weeks down to roughly 10 minutes "
        "demonstrated, while a shared deterministic Java gateway runs production traffic in under 5 ms."
    )

    # =========================================================================
    # SLIDE 3: Advantages #1 & #2 — Faster Go-Live & Lower Integration Cost
    # =========================================================================
    s3 = prs.slides.add_slide(L_TITLE_BODY)
    set_title(s3, "2. Faster Time to Go-Live & Lower Cost of Integrating New Systems")
    remove_placeholder(s3, 1)

    add_card(
        s3, 0.67, 1.44, 5.80, 3.05,
        "Advantage 1: Faster Time to Go-Live (SE ↔ CM3)",
        [
            "Traditional (Estimated 3–4 Weeks): Analyze source & target APIs, write mapping/transformation code, build bidirectional flows & error handling, test, deploy, and troubleshoot.",
            "SchemaBridge (~10 Min Demonstrated): Import API contracts -> deterministic discovery -> Aviator ADT resolves semantic ambiguities -> engineer review -> publish contract.",
            "Enabling Tech: 5-Level Matcher handles deterministic structure first so Aviator focuses only on true semantic gaps.",
        ],
        header_color=OT_NAVY, title_size=11.6, body_size=9.2,
    )

    add_card(
        s3, 6.85, 1.44, 5.80, 3.05,
        "Advantage 2: Lower Cost of Integrating New Systems",
        [
            "Traditional Adapter Sprawl: Each new system introduces another custom adapter/service to build, test, deploy, monitor, patch, upgrade, and maintain.",
            "Reusable Integration Platform: New systems onboard primarily through governed, versioned integration contracts on a shared gateway — avoiding a purpose-built service per integration.",
            "Honest Scope: Major behavioral or protocol changes may still require engineering code.",
        ],
        header_color=OT_TEAL, title_size=11.6, body_size=9.2,
    )

    # Visual Comparison Diagram at Bottom
    add_banner(
        s3, 0.67, 4.65, 5.80, 0.34,
        "TRADITIONAL: Separate Custom Adapter Lifecycle Per Integration",
        bg_color=OT_RED, font_size=9.6,
    )
    add_pill_box(s3, 0.67, 5.08, 5.80, 0.52, "System A  ->  Custom Adapter #1  ->  System B", "Build · Test · Deploy · Monitor · Patch", fill_color=OT_SOFT_BG, text_color=OT_CHARCOAL, title_size=9.2, sub_size=7.8)
    add_pill_box(s3, 0.67, 5.66, 5.80, 0.52, "System A  ->  Custom Adapter #2  ->  System C", "Build · Test · Deploy · Monitor · Patch", fill_color=OT_SOFT_BG, text_color=OT_CHARCOAL, title_size=9.2, sub_size=7.8)
    add_pill_box(s3, 0.67, 6.24, 5.80, 0.52, "System A  ->  Custom Adapter #3  ->  System D", "Build · Test · Deploy · Monitor · Patch", fill_color=OT_SOFT_BG, text_color=OT_CHARCOAL, title_size=9.2, sub_size=7.8)

    add_banner(
        s3, 6.85, 4.65, 5.80, 0.34,
        "SCHEMABRIDGE: Shared Gateway + Versioned Integration Contracts",
        bg_color=OT_DARK_NAVY, font_size=9.6,
    )
    add_pill_box(s3, 6.85, 5.08, 1.85, 1.68, "System A\nSystem B\nSystem C\nSystem D", "Disparate APIs", fill_color=OT_NAVY, text_color=OT_WHITE, title_size=9.6, sub_size=8.0)
    add_pill_box(s3, 8.82, 5.08, 3.83, 1.68, "SchemaBridge Gateway", "Versioned Integration Contracts\nGoverned Transformations\nCentralized Validation & Logs", fill_color=OT_TEAL, text_color=OT_WHITE, title_size=10.4, sub_size=8.6)
    set_notes(
        s3,
        "Here are our first two enterprise advantages. First, faster go-live: in our SE-to-CM3 "
        "demonstration, onboarding takes roughly 10 minutes demonstrated compared to an estimated "
        "three to four weeks of traditional custom integration work. Second, lower integration cost: "
        "instead of building and maintaining a separate custom adapter service for every new system, "
        "SchemaBridge onboards systems primarily through versioned contracts on a shared gateway."
    )

    # =========================================================================
    # SLIDE 4: Advantages #3 & #5 — Runtime Performance & Privacy-by-Architecture
    # =========================================================================
    s4 = prs.slides.add_slide(L_TITLE_BODY)
    set_title(s4, "3. Runtime Performance & Privacy-by-Architecture")
    remove_placeholder(s4, 1)

    add_banner(
        s4, 0.67, 1.44, 11.98, 0.42,
        "CORE PRINCIPLE:  AI designs the integration. Deterministic code runs it.",
        bg_color=OT_DARK_NAVY, font_size=11.0,
    )

    add_card(
        s4, 0.67, 2.02, 5.80, 3.10,
        "Advantage 3: Runtime Performance (Design vs. Runtime)",
        [
            "Design Time (AI-Assisted): Deterministic matching -> Aviator ADT semantic reasoning -> human approval -> publish contract.",
            "Runtime (Zero AI): Pure deterministic Java executes every production transformation.",
            "Demonstrated Result: <5 ms transformation latency and 0 production LLM calls.",
            "Bidirectional Support: Generates Write and Reverse Read flows from the same governed contract.",
        ],
        header_color=OT_NAVY, title_size=11.6, body_size=9.3,
    )

    add_card(
        s4, 6.85, 2.02, 5.80, 3.10,
        "Advantage 5: Data Privacy & Compliance Architecture",
        [
            "Design-Only AI Participation: OpenText Aviator ADT inspects API contracts/schemas at design time, never live production customer traffic.",
            "Deterministic Payload Execution: Production customer payloads are processed locally by the Java runtime and are not sent to any LLM.",
            "Privacy-by-Architecture: No live customer payload needs to be exposed to AI during runtime — supporting enterprise privacy and regulatory governance.",
        ],
        header_color=OT_TEAL, title_size=11.6, body_size=9.3,
    )

    add_pill_box(s4, 0.67, 5.32, 3.80, 1.38, "<5 ms Transformation", "Demonstrated in local Java gateway\n(No 2–5 s LLM round-trips)", fill_color=OT_BLUE, text_color=OT_WHITE, title_size=11.5, sub_size=8.6)
    add_pill_box(s4, 4.76, 5.32, 3.80, 1.38, "0 Production LLM Calls", "Zero runtime token cost &\ndeterministic repeatability", fill_color=OT_DARK_NAVY, text_color=OT_WHITE, title_size=11.5, sub_size=8.6)
    add_pill_box(s4, 8.85, 5.32, 3.80, 1.38, "No Live PII Sent to AI", "Privacy-by-architecture:\ncustomer payloads stay in runtime", fill_color=OT_TEAL, text_color=OT_WHITE, title_size=11.5, sub_size=8.6)
    set_notes(
        s4,
        "Our third and fifth advantages come from strictly separating AI design time from runtime. "
        "At design time, deterministic matching and Aviator ADT help engineers publish a verified "
        "contract. At runtime, pure Java runs every transformation in under 5 milliseconds demonstrated "
        "with zero production LLM calls. That gives us privacy by architecture: no live customer "
        "payload needs to be exposed to AI during runtime."
    )

    # =========================================================================
    # SLIDE 5: Advantages #4, #6 & #7 — API Drift, Observability & Less Infra Maintenance
    # =========================================================================
    s5 = prs.slides.add_slide(L_TITLE_BODY)
    set_title(s5, "4. API Drift, Observability & Reduced Infrastructure Maintenance")
    remove_placeholder(s5, 1)

    add_card(
        s5, 0.67, 1.44, 3.85, 4.55,
        "Advantage 4: API Drift & Maintenance",
        [
            "Traditional: API change -> production breakage or manual analysis -> find mapping code -> edit adapter -> rebuild -> regression test -> redeploy.",
            "SchemaBridge: Updated Swagger/contract -> detect schema drift -> evaluate impacted mappings -> flag broken/ambiguous paths -> engineer review -> republish version.",
            "Core Shift: From code maintenance to contract maintenance (major protocol/behavioral changes may still need engineering).",
        ],
        header_color=OT_NAVY, title_size=11.4, body_size=9.1,
    )

    add_card(
        s5, 4.74, 1.44, 3.85, 4.55,
        "Advantage 6: Observability & Debugging",
        [
            "Traditional: Inspect scattered logs, transformation code, and multiple services to locate failures.",
            "SchemaBridge Telemetry: Built-in correlation IDs, transformation logs, schema validation, unmapped-field detection, HTTP 422 quarantine handling, and mapping/version visibility.",
            "Live Demo Example: Invalid date or unmapped route quarantines before reaching CM3, showing exact failing rule & fix hint.",
        ],
        header_color=OT_BLUE, title_size=11.4, body_size=9.1,
    )

    add_card(
        s5, 8.81, 1.44, 3.84, 4.55,
        "Advantage 7: Less Tech & Infra Maintenance",
        [
            "Traditional Burden: Each custom integration service adds frameworks, libraries, dependency upgrades, CVE remediation, pipelines, infrastructure, monitoring, and ops ownership.",
            "Centralized Gateway: Consolidates integration execution into one reusable deterministic gateway with versioned contracts.",
            "Realistic Value: Reduces the number of purpose-built integration components and their ongoing maintenance burden.",
        ],
        header_color=OT_TEAL, title_size=11.4, body_size=9.1,
    )

    add_banner(
        s5, 0.67, 6.14, 11.98, 0.64,
        "Operational Shift:  From code maintenance & scattered debugging  ->  to governed contract evolution & observable integration flows.",
        bg_color=OT_DARK_NAVY, font_size=10.3,
    )
    set_notes(
        s5,
        "Keeping integrations running over time is where most teams bleed effort. When an API "
        "evolves, SchemaBridge detects contract drift, identifies impacted mappings, and lets an "
        "engineer review and republish a new contract version—moving from code maintenance to "
        "contract maintenance. During operations, correlation IDs, unmapped-field alerts, and HTTP 422 "
        "quarantine logs replace multi-service debugging, while a shared gateway reduces adapter sprawl."
    )

    # =========================================================================
    # SLIDE 6: Executive Comparison — Why SchemaBridge?
    # =========================================================================
    s6 = prs.slides.add_slide(L_TITLE_BODY)
    set_title(s6, "5. Why SchemaBridge?")
    remove_placeholder(s6, 1)

    add_comparison_table(s6, 0.67, 1.45, 11.98, 5.28)
    set_notes(
        s6,
        "This comparison summarizes why SchemaBridge is an enterprise integration accelerator rather "
        "than another mapping utility. Across all seven dimensions—time to go-live, new integration "
        "cost, runtime performance, API evolution, data privacy, debugging, and infrastructure "
        "maintenance—SchemaBridge replaces bespoke adapter lifecycles with governed, versioned "
        "integration contracts on a deterministic Java gateway."
    )

    # =========================================================================
    # SLIDE 7: Demo Story (SE <-> CM3) & Executive Takeaway
    # =========================================================================
    s7 = prs.slides.add_slide(L_TITLE_BODY)
    set_title(s7, "6. Live Demo Story (SE ↔ CM3) & Executive Takeaway")
    remove_placeholder(s7, 1)

    add_banner(
        s7, 0.67, 1.40, 11.98, 0.44,
        "Demo Story: \"CM3 is a new platform SE needs to integrate with. Let's take it from two API contracts to a governed, running integration.\"",
        bg_color=OT_DARK_NAVY, font_size=9.8,
    )

    demo_steps = [
        ("1. Two Systems", "SE & CM3 start\nindependent", OT_NAVY),
        ("2. Import Specs", "Load SE & CM3\nSwagger contracts", OT_NAVY),
        ("3. Discovery", "Run deterministic\nL1–L4 matching", OT_BLUE),
        ("4. Aviator AI", "Resolve semantic\nambiguity (L5)", OT_BLUE),
        ("5. Contract", "Inspect generated\nintegration rules", OT_BLUE),
        ("6. Approve", "Check confidence\n& human sign-off", OT_GOLD),
        ("7. Publish", "Lock versioned\ncontract", OT_GOLD),
        ("8. SE Request", "Send real SE\npayload to GW", OT_TEAL),
        ("9. CM3 (<5ms)", "Verify CM3 output\n& <5 ms latency", OT_TEAL),
        ("10. Reverse Read", "Execute CM3 -> SE\nread response", OT_TEAL),
        ("11. Quarantine", "Block invalid/unmapped\n(HTTP 422)", OT_RED),
        ("12. API Drift", "Update Swagger &\ndetect impacted rule", OT_DARK_NAVY),
    ]
    for i, (t, sub, col) in enumerate(demo_steps):
        row = i // 6
        col_i = i % 6
        fg = OT_DARK_NAVY if col == OT_GOLD else OT_WHITE
        add_pill_box(
            s7,
            0.67 + col_i * 2.01,
            1.96 + row * 0.94,
            1.90,
            0.84,
            t,
            sub,
            fill_color=col,
            text_color=fg,
            title_size=9.0,
            sub_size=7.7,
        )

    add_card(
        s7, 0.67, 3.96, 11.98, 1.24,
        "Integration Lifecycle Shift",
        [
            "Core Shift: SchemaBridge changes enterprise integration from a custom coding project into a governed onboarding and lifecycle-management workflow.",
            "Observable Safety: Live Gateway telemetry (correlation IDs, applied rules, unmapped fields, HTTP 422 quarantine) validates every call before System B is touched.",
        ],
        header_color=OT_NAVY, title_size=11.2, body_size=9.3,
    )

    add_banner(
        s7, 0.67, 5.34, 11.98, 1.44,
        "Executive Takeaway:\n"
        "Faster go-live. Lower integration cost. High-performance runtime. Easier API evolution.\n"
        "No production data sent to AI. Better operational visibility. Less custom integration infrastructure to maintain.",
        bg_color=OT_TEAL, font_size=11.0,
    )
    set_notes(
        s7,
        "Our live demo tells a clear story: CM3 is a new platform SE needs to integrate with. "
        "In 12 quick steps, we go from two independent Swagger contracts to deterministic discovery, "
        "Aviator AI semantic resolution, human approval and publishing, live Write and Reverse Read "
        "execution in under 5 ms, HTTP 422 quarantine handling, and API drift detection. "
        "SchemaBridge turns enterprise integration from a custom coding project into a governed "
        "onboarding and lifecycle-management workflow."
    )

    # =========================================================================
    # SLIDE 8: OpenText Back Cover (Closing Slide)
    # =========================================================================
    prs.slides.add_slide(L_BACK)

    # =========================================================================
    # BACKUP SLIDES (After Closing Slide): Technical Deep-Dives + Screenshots
    # =========================================================================
    b1 = prs.slides.add_slide(L_TITLE_BODY)
    set_title(b1, "Backup: Enabling Tech — 5-Level Matching Funnel & Workspace")
    remove_placeholder(b1, 1)
    add_card(
        b1, 0.67, 1.48, 5.25, 5.32,
        "5-Level Residual Matching Cascade",
        [
            "L1 — Exact Match (Deterministic, Free): Identical names & type coercion.",
            "L2 — Normalization (Deterministic, Free): snake_case <-> camelCase, dates, booleans.",
            "L3 — Business Synonyms (Deterministic, Free): acct_num -> tenantAccountId, dob -> dateOfBirth.",
            "L4 — Approved Memory (Deterministic, Free): Reuses prior approved mappings.",
            "L5 — OpenText Aviator ADT (AI-Assisted): Resolves complex/nested semantic gaps & plain-English rules.",
        ],
        header_color=OT_NAVY, title_size=11.6, body_size=9.2,
    )
    add_framed_screenshot(b1, IMG_MAPPING_WORKSPACE, 6.10, 1.48, 6.55, 3.55, border_color=OT_BLUE)
    add_card(
        b1, 6.10, 5.18, 6.55, 1.62,
        "Confidence-Gated Review & 1-Click Reverse Read",
        [
            "Rules <90% confidence pause in the review queue for engineer approval before publishing.",
            "1-click inversion generates Reverse Read contracts from Forward Write contracts.",
        ],
        header_color=OT_BLUE, title_size=11.4, body_size=9.2,
    )

    b2 = prs.slides.add_slide(L_TITLE_BODY)
    set_title(b2, "Backup: Live 3-URL Gateway Simulator (All 6 Enterprise Cases)")
    remove_placeholder(b2, 1)
    add_card(
        b2, 0.67, 1.48, 5.25, 5.32,
        "All 6 Live Gateway Cases (SE ↔ CM3)",
        [
            "Case 1 — Normal Write: SE payload -> CM3 schema (HTTP 201, <5 ms demonstrated).",
            "Case 2 — Reverse Read: CM3 response -> SE legacy format (HTTP 200).",
            "Case 3 — Extra Unknown Fields: Flags in log & applies saved policy (Drop / Pass / Block).",
            "Case 4 — Missing Mapping: Quarantines unmapped route (HTTP 422).",
            "Case 5 — Invalid Date Format: Rejects malformed date with exact rule diagnostic.",
            "Case 6 — Missing Required ID: Blocks payload missing required key before System B write.",
        ],
        header_color=OT_DARK_NAVY, title_size=11.6, body_size=9.1,
    )
    add_framed_screenshot(b2, IMG_GATEWAY_6CASES, 6.10, 1.48, 6.55, 3.55, border_color=OT_TEAL)
    add_card(
        b2, 6.10, 5.18, 6.55, 1.62,
        "Observable Integration Telemetry",
        [
            "Every request logs correlation ID, applied rules, unmapped fields, and target validation.",
        ],
        header_color=OT_TEAL, title_size=11.4, body_size=9.2,
    )

    # Retain sanitized deep-dive slides from existing deck after new backup slides
    old_backup_indices = [7, 8, 9, 10, 11]
    for b_idx in old_backup_indices:
        sanitize_backup_slide(prs.slides[b_idx], title_prefix="Backup: ")

    # New slides were appended at indices old_count .. old_count + 9 (8 main/back + 2 new backups)
    new_main_and_back = list(range(old_count, old_count + 8))       # Slides 1..7 + Back cover
    new_screenshot_backups = list(range(old_count + 8, old_count + 10))
    reorder_and_prune_slides(prs, new_main_and_back + new_screenshot_backups + old_backup_indices)

    prs.save(str(OUTPUT_PPTX))
    return OUTPUT_PPTX


if __name__ == "__main__":
    out = build_presentation()
    prs_check = Presentation(str(out))
    print(f"Saved presentation to: {out} ({len(prs_check.slides)} total slides)")