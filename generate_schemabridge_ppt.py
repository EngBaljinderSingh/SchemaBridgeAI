"""Generate 'SchemaBridge AI Aviator.pptx' using the OpenText Aviator master template."""

from __future__ import annotations

from pathlib import Path
from pptx import Presentation
from pptx.dml.color import RGBColor
from pptx.enum.shapes import MSO_SHAPE
from pptx.enum.text import MSO_ANCHOR, PP_ALIGN
from pptx.util import Inches, Pt

ROOT = Path(__file__).resolve().parent
TEMPLATE_PPTX = Path(r"C:\Users\baljinders\Documents\GitHub\AutoVerbum\CC4E Aviator.pptx")
FALLBACK_TEMPLATE_PPTX = Path(r"C:\Users\baljinders\Documents\GitHub\AutoVerbum\QE Agent Aviator.pptx")
OUTPUT_PPTX = ROOT / "SchemaBridge AI Aviator.pptx"

SCREENSHOT_MAPPING = Path(
    r"C:/Users/baljinders/.gemini/antigravity/brain/00302878-1269-4261-80d4-f9ff35bac9f3/.user_uploaded/media_1794747428876_d19b37e7.jpg"
)
SCREENSHOT_APPS = Path(
    r"C:/Users/baljinders/.gemini/antigravity/brain/00302878-1269-4261-80d4-f9ff35bac9f3/.user_uploaded/media_1794747445408_c73838a5.jpg"
)

# OpenText Theme Palette (from CC4E Aviator.pptx / QE Agent Aviator.pptx theme1.xml)
OT_NAVY = RGBColor(0x00, 0x00, 0x8A)       # accent1: #00008A
OT_BLUE = RGBColor(0x19, 0x68, 0xFF)       # accent2: #1968FF
OT_DARK_NAVY = RGBColor(0x00, 0x1C, 0x50)  # accent6: #001C50
OT_SLATE = RGBColor(0x66, 0x67, 0x7B)      # accent3: #66677B
OT_LIGHT_GRAY = RGBColor(0xE7, 0xE9, 0xEC) # accent4: #E7E9EC
OT_SOFT_BG = RGBColor(0xF4, 0xF6, 0xFA)    # subtle card fill
OT_GOLD = RGBColor(0xE1, 0xBB, 0x35)       # accent5: #E1BB35
OT_TEAL = RGBColor(0x0D, 0x94, 0x88)       # success/healing accent
OT_CHARCOAL = RGBColor(0x37, 0x38, 0x48)   # lt2: #373848
OT_RED = RGBColor(0x99, 0x1B, 0x1B)        # alert/problem accent
OT_AMBER = RGBColor(0xB4, 0x53, 0x09)      # warning/fallback accent
OT_WHITE = RGBColor(0xFF, 0xFF, 0xFF)


def clear_slides(prs: Presentation) -> None:
    while len(prs.slides) > 0:
        r_id = prs.slides._sldIdLst[0].rId
        prs.part.drop_rel(r_id)
        del prs.slides._sldIdLst[0]


def update_master_footer_year(prs: Presentation, old_year: str = "2025", new_year: str = "2026") -> None:
    """Update copyright footer text across all slide masters and slide layouts."""
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
    title_size: float = 13,
    body_size: float = 10.5,
) -> None:
    # Card body
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

    # Header bar
    hdr_h = 0.44
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

    # Content text box
    tb = slide.shapes.add_textbox(
        Inches(left + 0.12),
        Inches(top + hdr_h + 0.08),
        Inches(width - 0.24),
        Inches(height - hdr_h - 0.14),
    )
    tf = tb.text_frame
    tf.word_wrap = True
    tf.margin_left = Inches(0.04)
    tf.margin_right = Inches(0.04)
    tf.margin_top = Inches(0.02)

    for i, item in enumerate(bullets):
        p = tf.paragraphs[0] if i == 0 else tf.add_paragraph()
        p.space_after = Pt(5)
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
    font_size: float = 11.5,
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
    tf.margin_left = Inches(0.18)
    tf.margin_right = Inches(0.18)
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
    title_size: float = 11,
    sub_size: float = 9,
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
    tf.margin_left = Inches(0.08)
    tf.margin_right = Inches(0.08)

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
        p2.space_before = Pt(2)
        p2.text = subtitle
        p2.font.name = "Arial"
        p2.font.size = Pt(sub_size)
        p2.font.color.rgb = text_color


def build_presentation() -> Path:
    tpl = TEMPLATE_PPTX if TEMPLATE_PPTX.is_file() else FALLBACK_TEMPLATE_PPTX
    prs = Presentation(str(tpl))
    clear_slides(prs)
    update_master_footer_year(prs, "2025", "2026")

    layouts = prs.slide_layouts
    L_COVER = layouts[0]      # 'Cover'
    L_DIVIDER = layouts[1]    # 'Divider 2'
    L_TITLE_BODY = layouts[3] # 'Title | Body'
    L_BACK = layouts[28]      # 'Back cover'

    # =========================================================================
    # SLIDE 1: Cover (Layout: Cover)
    # =========================================================================
    s1 = prs.slides.add_slide(L_COVER)
    s1.placeholders[10].text = "SchemaBridge AI"
    s1.placeholders[11].text = "Aviator-Powered Intelligent Schema & Payload Translation Platform"
    s1.placeholders[12].text = (
        "Baljinder Singh, Integration & Platform Engineering\n"
        "Powered by OpenText Aviator ADT  |  AI-Advisory Design Time · Zero-AI <5ms Deterministic Runtime"
    )

    # =========================================================================
    # SLIDE 2: Agenda (Layout: Title | Body)
    # =========================================================================
    s2 = prs.slides.add_slide(L_TITLE_BODY)
    set_title(s2, "Agenda")
    remove_placeholder(s2, 1)

    add_card(
        s2, 0.67, 1.55, 5.8, 2.45,
        "1. Vision & Core Architectural Principle",
        [
            "Why traditional integrations & live-LLM proxies fail: Weeks of brittle hand-coding vs. slow, costly, and risky live AI calls.",
            "The SchemaBridge AI breakthrough: Decoupled Two-Phase Architecture — AI advises once at design time; pure Java executes at runtime.",
            "Zero PII to AI: Customer payloads never leave your network or touch an LLM in production (<5ms latency, 100% deterministic).",
        ],
        header_color=OT_NAVY,
    )
    add_card(
        s2, 6.85, 1.55, 5.8, 2.45,
        "2. 5-Level Residual Matching Cascade & Aviator AI",
        [
            "Deterministic-first field matching: Resolves Exact, Normalised, Synonym, and Historical rules locally at zero token cost.",
            "Surgical OpenText Aviator ADT invocation: Calls LLM strictly for residual unresolved fields and natural language rule prompts.",
            "3-Column Interactive Mapping Workspace: Visual side-by-side schema trees, confidence scoring, and one-click human approval.",
        ],
        header_color=OT_BLUE,
    )
    add_card(
        s2, 0.67, 4.25, 5.8, 2.55,
        "3. Zero-AI Runtime Engine & Bi-Directional Proxy",
        [
            "20 Built-In Algebraic Operations: Rename, Date Format, Flatten, Nest, Enum Map, Type Coercion, and Conditional logic in pure Java.",
            "Automated Bi-Directional Inversion: Forward WRITE rules (System A -> B) automatically synthesize reverse READ rules (System B -> A).",
            "High-Throughput Jackson AST Engine: Sub-5ms in-memory transformation with full offline resilience even if AI services are unreachable.",
        ],
        header_color=OT_DARK_NAVY,
    )
    add_card(
        s2, 6.85, 4.25, 5.8, 2.55,
        "4. Schema Drift Detection, Governance & Patent Claims",
        [
            "Closed-Loop Cryptographic Drift Detection: SHA-256 schema hashing pinpoints broken rules (BROKEN_PATH) before production fails.",
            "Enterprise Governance: Draft -> Approved -> Published lifecycle gates, immutable versions, and MDC correlation audit trails.",
            "5 Patent-Pending Innovations: Provisional patent architecture delivering 90%+ faster integration setup and zero runtime AI cost.",
        ],
        header_color=OT_TEAL,
    )

    # =========================================================================
    # SLIDE 3: Use Cases & Core Problem Solved (Layout: Title | Body)
    # =========================================================================
    s3 = prs.slides.add_slide(L_TITLE_BODY)
    set_title(s3, "Use Cases & Core Problem Solved")
    remove_placeholder(s3, 1)

    add_card(
        s3, 0.67, 1.50, 5.8, 4.45,
        "Why Traditional Integration & Live-LLM Approaches Fail",
        [
            "Manual Hand-Coding Bottlenecks: Writing custom adapter code for hundreds of legacy snake_case and modern camelCase fields takes weeks.",
            "Unacceptable Live LLM Latency (1–5 Seconds): Production API gateways require sub-10ms responses; calling an LLM per payload stalls traffic.",
            "Hallucinations & Non-Determinism: LLMs randomly drop fields, alter numeric precision, or produce invalid JSON under load.",
            "Severe Data Privacy & Compliance Risks: Sending live customer PII, health records, or financial payloads to external LLMs violates GDPR & HIPAA.",
            "Runaway Token Economics & Silent API Breakage: Paying LLM token fees on millions of daily transactions is cost-prohibitive, and schema changes break silently.",
        ],
        header_color=OT_RED,
    )
    add_card(
        s3, 6.85, 1.50, 5.8, 4.45,
        "How SchemaBridge AI Solves It",
        [
            "AI Advisory at Design Time Only: OpenText Aviator ADT analyzes schema metadata once to recommend mappings and translate plain-English rules.",
            "Zero-AI Pure Java Runtime (<5ms): Live payloads execute 100% deterministically in memory via compiled algebraic rules — zero PII ever touches AI.",
            "5-Level Residual Matching Cascade: Resolves 80%+ of fields deterministically first; sends only leftover unknown fields to Aviator AI.",
            "Automated Bi-Directional Proxy: Translates both Forward WRITE (Legacy -> Cloud) and Reverse READ (Cloud -> Legacy) with zero legacy code changes.",
            "Cryptographic Schema Drift Guardrails: SHA-256 fingerprinting detects upstream/downstream API changes and flags affected rules immediately.",
        ],
        header_color=OT_NAVY,
    )
    add_banner(
        s3, 0.67, 6.15, 11.98, 0.65,
        "Key Outcome: Map complex enterprise schemas in minutes with AI — execute millions of live payloads in <5ms with 0 AI cost and 100% privacy.",
        bg_color=OT_BLUE,
    )

    # =========================================================================
    # SLIDE 4: Two-Phase Decoupled Architecture & Human Governance
    # =========================================================================
    s4 = prs.slides.add_slide(L_TITLE_BODY)
    set_title(s4, "Two-Phase Decoupled Architecture & Governance")
    remove_placeholder(s4, 1)

    # Phase 1 Row
    add_banner(
        s4, 0.67, 1.45, 11.98, 0.42,
        "PHASE 1 — DESIGN-TIME AI-ADVISORY DISCOVERY & HUMAN-IN-THE-LOOP APPROVAL (Offline / Zero Live PII)",
        bg_color=OT_DARK_NAVY, font_size=10.5,
    )
    phase1_steps = [
        ("1. Ingest Schemas", "JSON Schema / Sample\nOpenAPI / XML / CSV", OT_NAVY),
        ("2. Levels 1–4 Match", "Exact, Normalised,\nSynonym & History", OT_NAVY),
        ("3. Level 5 Aviator AI", "Residual unknown\nfields + NL rules", OT_BLUE),
        ("4. Confidence Gate", "Scores rules & flags\n<70% for review", OT_BLUE),
        ("HUMAN GATE", "Review, edit & approve\nin 3-Column UI", OT_GOLD),
        ("6. Publish Version", "Immutable versioned\ncontract (v1, v2...)", OT_TEAL),
    ]
    for i, (t, sub, col) in enumerate(phase1_steps):
        fg = OT_DARK_NAVY if col == OT_GOLD else OT_WHITE
        add_pill_box(s4, 0.67 + i * 2.02, 2.00, 1.90, 1.05, t, sub, fill_color=col, text_color=fg)

    # Phase 2 Row
    add_banner(
        s4, 0.67, 3.25, 11.98, 0.42,
        "PHASE 2 — LIVE PRODUCTION ZERO-AI DETERMINISTIC EXECUTION (<5ms In-Memory Java Engine)",
        bg_color=OT_DARK_NAVY, font_size=10.5,
    )
    phase2_steps = [
        ("7. System A Payload", "Legacy POS / HR\nsnake_case JSON", OT_NAVY),
        ("8. Algebraic Engine", "20 pure-Java ops\nin Jackson AST (<5ms)", OT_TEAL),
        ("9. Schema Validator", "Strict target JSON\nSchema verification", OT_TEAL),
        ("10. System B Delivery", "Cloud ERP / CRM\ncamelCase ISO JSON", OT_BLUE),
        ("11. Reverse Flow", "Auto-synthesized\nRead-back inversion", OT_BLUE),
        ("12. Audit & Drift", "SHA-256 drift check\n& MDC audit trail", OT_NAVY),
    ]
    for i, (t, sub, col) in enumerate(phase2_steps):
        add_pill_box(s4, 0.67 + i * 2.02, 3.80, 1.90, 1.05, t, sub, fill_color=col, text_color=OT_WHITE)

    # Bottom 3 architectural pillar cards
    add_card(
        s4, 0.67, 5.05, 3.85, 1.85,
        "100% Data Privacy (Zero PII to AI)",
        [
            "Only structural metadata (field names & types) is evaluated during Phase 1.",
            "Live customer payloads in Phase 2 never leave your JVM or call external AI APIs.",
        ],
        header_color=OT_SLATE, title_size=11.5, body_size=9.5,
    )
    add_card(
        s4, 4.73, 5.05, 3.85, 1.85,
        "Offline & Outage Resilience",
        [
            "If OpenText Aviator ADT or cloud network is unreachable, Levels 1–4 still match deterministically.",
            "Published runtime transformations execute with zero dependency on AI availability.",
        ],
        header_color=OT_SLATE, title_size=11.5, body_size=9.5,
    )
    add_card(
        s4, 8.80, 5.05, 3.85, 1.85,
        "Strict Lifecycle Enforcement",
        [
            "Production execution endpoint (/transform/execute) accepts ONLY published mappings (HTTP 422 guard).",
            "Any edit to an approved rule automatically resets status to DRAFT and requires re-approval.",
        ],
        header_color=OT_SLATE, title_size=11.5, body_size=9.5,
    )

    # =========================================================================
    # SLIDE 5: Multi-Format Schema Ingestion & Canonical AST Extraction
    # =========================================================================
    s5 = prs.slides.add_slide(L_TITLE_BODY)
    set_title(s5, "Multi-Source Schema Ingestion & Canonical AST Extraction")
    remove_placeholder(s5, 1)

    add_card(
        s5, 0.67, 1.50, 2.85, 4.45,
        "Sample JSON Payloads",
        [
            "Zero-Spec Onboarding: Paste raw legacy JSON payloads when formal schemas do not exist.",
            "Automatic Pattern Inference: Detects date formats (dd/MM/yyyy vs ISO-8601), boolean strings ('Yes'/'No'), numbers, and nested objects.",
            "Flattened Dot-Path Indexing: Indexes deep paths (e.g., contact.email) into canonical SchemaNode trees.",
        ],
        header_color=OT_NAVY, title_size=12, body_size=10,
    )
    add_card(
        s5, 3.71, 1.50, 2.85, 4.45,
        "Formal JSON Schema & OpenAPI",
        [
            "Strict Contract Parsing: Ingests Draft-07 / 2020-12 JSON Schemas and OpenAPI / Swagger definitions.",
            "Constraint Extraction: Preserves required flags, enum allowlists, format patterns, and field descriptions.",
            "Runtime Validation Gate: Enforces target schema compliance on every transformed payload before delivery.",
        ],
        header_color=OT_BLUE, title_size=12, body_size=10,
    )
    add_card(
        s5, 6.75, 1.50, 2.85, 4.45,
        "API Registry & Connectors",
        [
            "Live System Registration: Register System A and System B endpoints in the built-in API Registry.",
            "Bi-Directional Proxy Routing: Exposes /api/demo/write and /api/demo/read proxy pipelines between registered apps.",
            "Multi-Project Isolation: Manage independent integration pipelines (e.g., SEtoCM3, HR-to-AD, POS-to-ERP).",
        ],
        header_color=OT_DARK_NAVY, title_size=12, body_size=10,
    )
    add_card(
        s5, 9.80, 1.50, 2.85, 4.45,
        "SHA-256 Schema Fingerprinting",
        [
            "Cryptographic Baseline: Computes a deterministic SHA-256 hash over normalized schema structure on import.",
            "Version History Tracking: Stores every schema revision per project (Source & Target) in PostgreSQL / H2.",
            "Instant Drift Comparison: Powers structural tree diffing whenever upstream or downstream contracts evolve.",
        ],
        header_color=OT_TEAL, title_size=12, body_size=10,
    )
    add_banner(
        s5, 0.67, 6.15, 11.98, 0.65,
        "Design Principle: Normalize any legacy payload or modern API contract into a unified SchemaNode AST with cryptographic version control.",
        bg_color=OT_DARK_NAVY,
    )

    # =========================================================================
    # SLIDE 6: The 5-Level Residual Matching Cascade
    # =========================================================================
    s6 = prs.slides.add_slide(L_TITLE_BODY)
    set_title(s6, "5-Level Residual Matching Cascade (Patent-Pending)")
    remove_placeholder(s6, 1)

    tiers = [
        (
            "LEVEL 1: Exact Name Match (Confidence: 100% — Deterministic · 0 Token Cost)",
            "Matches identical source and target field paths (e.g., id -> id, customerId -> customerId) and automatically infers required type coercions.",
            OT_NAVY,
        ),
        (
            "LEVEL 2: Normalised Structural Match (Confidence: 95% — Deterministic · 0 Token Cost)",
            "Strips technical prefixes/suffixes (tbl_, _id, _num) and normalizes snake_case, camelCase, and PascalCase (e.g., total_amount -> totalAmount, project_id -> projectId).",
            OT_BLUE,
        ),
        (
            "LEVEL 3: Domain Synonym Dictionary Match (Confidence: 93% — Deterministic · 0 Token Cost)",
            "Consults a persisted, configurable enterprise synonym repository (e.g., name -> userName, dob -> dateOfBirth, active -> accountEnabled, cust_id -> customerId).",
            OT_DARK_NAVY,
        ),
        (
            "LEVEL 4: Historical Approved Rule Reuse (Confidence: 90% — Deterministic · 0 Token Cost)",
            "Queries previously approved and published MappingRule records across the domain to reuse proven operations and format parameters automatically.",
            OT_TEAL,
        ),
        (
            "LEVEL 5: OpenText Aviator ADT Semantic AI (Invoked ONLY for Residual Unmapped Fields)",
            "Sends only the leftover unresolved source/target nodes to Aviator ADT with strict JSON Schema output constraints and prompt-injection sandboxing (e.g., contact.email -> emailAddress).",
            OT_AMBER,
        ),
    ]
    for idx, (heading, desc, col) in enumerate(tiers):
        y = 1.48 + idx * 1.06
        add_card(
            s6, 0.67, y, 11.98, 0.96,
            heading,
            [desc],
            header_color=col, title_size=11.5, body_size=10,
        )

    # =========================================================================
    # SLIDE 7: Interactive 3-Column Mapping Workspace & Aviator NL Assistant (Screenshot 1)
    # =========================================================================
    s7 = prs.slides.add_slide(L_TITLE_BODY)
    set_title(s7, "Interactive 3-Column Mapping Workspace & Aviator AI")
    remove_placeholder(s7, 1)

    add_card(
        s7, 0.67, 1.50, 4.95, 5.30,
        "Visual Mapping & Natural Language AI",
        [
            "3-Column Mapping Studio: Displays Source Fields (left), Active Mapping Rules with confidence & match badges (center), and Target Fields with format constraints (right).",
            "Natural Language Rule Assistant: Type plain English (e.g., 'Set accountEnabled to true when active is Yes' or 'Combine firstName and lastName into displayName') and click 'Convert with Aviator AI'.",
            "Transparent Provenance Badges: Every rule displays its exact confidence score (e.g., 95% HIGH) and origin tier (NORMALISED, SYNONYM, HISTORICAL, AI_SEMANTIC).",
            "Granular Human-in-the-Loop Controls: Filter by 'Review Required', edit rule operations/parameters inline, reject bad suggestions, or click 'Approve All' & 'Publish v4'.",
            "Live Aviator Telemetry Pill: Header continuously monitors OpenText Aviator AI connectivity and response latency (e.g., Connected · 241ms).",
        ],
        header_color=OT_NAVY, title_size=12, body_size=9.8,
    )

    if SCREENSHOT_MAPPING.is_file():
        frame = s7.shapes.add_shape(
            MSO_SHAPE.ROUNDED_RECTANGLE,
            Inches(5.80),
            Inches(1.50),
            Inches(6.85),
            Inches(3.60),
        )
        frame.adjustments[0] = 0.02
        frame.fill.solid()
        frame.fill.fore_color.rgb = OT_WHITE
        frame.line.color.rgb = OT_BLUE
        frame.line.width = Pt(1.5)

        s7.shapes.add_picture(
            str(SCREENSHOT_MAPPING),
            Inches(5.88),
            Inches(1.58),
            width=Inches(6.69),
        )

    add_card(
        s7, 5.80, 5.25, 6.85, 1.55,
        "Live SchemaBridge AI UI — 3-Column Mapping Workspace (Version v4)",
        [
            "Active Rules Shown: total_amount -> totalAmount (95% HIGH · NORMALISED) and name -> userName (93% HIGH · SYNONYM).",
            "Lifecycle Governance: Clone immutable versions, generate 5-level suggestions on demand, and publish verified rules to production.",
        ],
        header_color=OT_BLUE, title_size=11.5, body_size=9.5,
    )

    # =========================================================================
    # SLIDE 8: Multi-Project Integration Hub & API Registry (Screenshot 2)
    # =========================================================================
    s8 = prs.slides.add_slide(L_TITLE_BODY)
    set_title(s8, "Multi-Project Integration Hub & End-to-End Studio")
    remove_placeholder(s8, 1)

    add_card(
        s8, 0.67, 1.50, 4.95, 5.30,
        "3-URL Gateway Hub & 6-Case Live Telemetry",
        [
            "Explicit 3-URL Architecture: Connects (1) Source System A URL -> (2) SchemaBridge Gateway Proxy URL (/api/gateway/{id}/...) -> (3) Target System B URL.",
            "2 Complete Pre-Seeded Projects: Includes SEtoCM3 (Subscription Engine <-> Cloud Metering CM3) and LegacyPOS-to-CloudERP out of the box.",
            "6 Live Enterprise Cases: Simulate Happy WRITE (201), Reverse READ (200), Unmapped Fields Warning, Missing Mapping Quarantine (422), Transform Error (400), and Schema Validation Error (422).",
            "Missing Mapping & Unmapped Policies: Configurable DROP_WITH_WARNING, PASS_THROUGH, or STRICT_REJECT_422 quarantine with payload capture.",
            "Full Error & Trace Inspector: Logs every correlationId, failing rule diagnostic, unmapped field list, and actionable resolution hint.",
        ],
        header_color=OT_DARK_NAVY, title_size=12, body_size=9.6,
    )

    if SCREENSHOT_APPS.is_file():
        frame2 = s8.shapes.add_shape(
            MSO_SHAPE.ROUNDED_RECTANGLE,
            Inches(5.80),
            Inches(1.50),
            Inches(6.85),
            Inches(3.60),
        )
        frame2.adjustments[0] = 0.02
        frame2.fill.solid()
        frame2.fill.fore_color.rgb = OT_WHITE
        frame2.line.color.rgb = OT_BLUE
        frame2.line.width = Pt(1.5)

        s8.shapes.add_picture(
            str(SCREENSHOT_APPS),
            Inches(5.88),
            Inches(1.58),
            width=Inches(6.69),
        )

    add_card(
        s8, 5.80, 5.25, 6.85, 1.55,
        "Live SchemaBridge AI UI — Integration Projects & Apps Dashboard",
        [
            "Project Card (SEtoCM3): Tracks 4 mapping versions with one-click workspace launch and lifecycle status badges.",
            "Angular 21 Standalone UI: Modern dark-mode glassmorphic interface with real-time Aviator AI health telemetry.",
        ],
        header_color=OT_TEAL, title_size=11.5, body_size=9.5,
    )

    # =========================================================================
    # SLIDE 9: Section Divider (Layout: Divider 2)
    # =========================================================================
    s9 = prs.slides.add_slide(L_DIVIDER)
    s9.placeholders[0].text = "Zero-AI Runtime, Bi-Directional Proxy & Governance"
    s9.placeholders[1].text = "Sub-5ms deterministic execution, 20 algebraic operations, schema drift detection, and patent claims"

    # =========================================================================
    # SLIDE 10: Pure-Java Deterministic Runtime & 20 Algebraic Operations
    # =========================================================================
    s10 = prs.slides.add_slide(L_TITLE_BODY)
    set_title(s10, "Zero-AI Deterministic Runtime & 20 Algebraic Operations")
    remove_placeholder(s10, 1)

    add_card(
        s10, 0.67, 1.50, 5.80, 2.50,
        "In-Memory Jackson AST Transformation Engine (<5ms)",
        [
            "100% Deterministic Execution: Compiles approved MappingRules into pure Java AST operations — zero LLM calls, zero hallucinations, and zero token cost.",
            "Strict Target JSON Schema Validation: Validates every transformed output payload against the target system's contract before forwarding.",
            "Draft Preview vs. Production Gate: Test draft mappings safely in Transformation Lab (/preview), while production (/execute) enforces published-only versions.",
        ],
        header_color=OT_NAVY, title_size=12, body_size=9.8,
    )
    add_card(
        s10, 6.85, 1.50, 5.80, 2.50,
        "Automated Bi-Directional Schema Inversion (WRITE & READ)",
        [
            "Single-Definition Dual Direction: Define forward WRITE rules (System A -> System B) once; SchemaBridge automatically synthesizes the reverse READ rules.",
            "Symmetric Structural Reversal: Automatically inverts FLATTEN <-> NEST, DATE_FORMAT patterns, BOOLEAN <-> STRING, and ENUM_MAP dictionaries.",
            "Zero Legacy Refactoring: Legacy System A both writes to and reads from modern Cloud System B in its native legacy format.",
        ],
        header_color=OT_BLUE, title_size=12, body_size=9.8,
    )

    add_banner(
        s10, 0.67, 4.18, 11.98, 0.40,
        "20 SUPPORTED DETERMINISTIC TRANSFORMATION OPERATIONS (EXECUTED IN PURE JAVA AT <5ms LATENCY)",
        bg_color=OT_DARK_NAVY, font_size=10.5,
    )
    op_cards = [
        (
            "Structural & Path Ops",
            [
                "RENAME: Direct key translation.",
                "FLATTEN: Collapse nested paths (contact.email -> emailAddress).",
                "NEST: Construct deep object hierarchies.",
                "ARRAY_MAP / COPY / REMOVE: Collection & field routing.",
            ],
            OT_NAVY,
        ),
        (
            "Type & Date Coercion",
            [
                "DATE_FORMAT: e.g., dd/MM/yyyy <-> yyyy-MM-dd.",
                "STRING_TO_BOOLEAN & BOOLEAN_TO_STRING ('Yes' <-> true).",
                "STRING_TO_NUMBER & NUMBER_TO_STRING (1001 <-> '1001').",
            ],
            OT_BLUE,
        ),
        (
            "Business Logic & Enums",
            [
                "ENUM_MAP: Translate codes ('GOLD_TIER' <-> 'GLD').",
                "CONDITIONAL: Rule-based branching expressions.",
                "DEFAULT_VALUE: Fallback when source is null/missing.",
                "CONSTANT_VALUE: Inject static target attributes.",
            ],
            OT_TEAL,
        ),
        (
            "String Manipulation",
            [
                "CONCAT: Merge multiple fields (firstName + lastName).",
                "SPLIT: Tokenize composite strings into fields.",
                "TRIM / UPPERCASE / LOWERCASE: Canonical text normalization.",
            ],
            OT_SLATE,
        ),
    ]
    for i, (o_title, o_bullets, o_col) in enumerate(op_cards):
        add_card(
            s10, 0.67 + i * 3.04, 4.72, 2.86, 2.18,
            o_title, o_bullets, header_color=o_col, title_size=11, body_size=9.2,
        )

    # =========================================================================
    # SLIDE 11: Live Bi-Directional Demo Scenario: System A <-> System B
    # =========================================================================
    s11 = prs.slides.add_slide(L_TITLE_BODY)
    set_title(s11, "Live Bi-Directional Walkthrough: Legacy POS <-> Cloud ERP")
    remove_placeholder(s11, 1)

    add_card(
        s11, 0.67, 1.50, 3.85, 4.45,
        "System A: Legacy Core POS Payload",
        [
            "order_num: 'ORD-2026-991' (snake_case string)",
            "cust_id: 'CUST-4081' (abbreviated legacy key)",
            "name: 'Dr. Sarah Connor' (flat customer name)",
            "dob: '15/08/1988' (slash date dd/MM/yyyy)",
            "active: 'Yes' (string boolean flag)",
            "tier: 'GOLD_TIER' (verbose legacy enum)",
            "contact.email: 'sarah.c@cyberdyne.org' (nested object)",
            "total_amount: 499.99 (snake_case decimal)",
        ],
        header_color=OT_SLATE, title_size=11.5, body_size=9.8,
    )
    add_card(
        s11, 4.73, 1.50, 3.85, 4.45,
        "SchemaBridge Rules (<3ms Pure Java)",
        [
            "order_num -> orderId: RENAME (92% · NORMALISED)",
            "cust_id -> customerId: RENAME (93% · SYNONYM)",
            "name -> userName: RENAME (93% · SYNONYM)",
            "dob -> dateOfBirth: DATE_FORMAT dd/MM/yyyy -> yyyy-MM-dd (95% · SYNONYM)",
            "active -> accountEnabled: STRING_TO_BOOLEAN (93% · SYNONYM)",
            "tier -> membershipLevel: ENUM_MAP GOLD_TIER -> GLD (90% · AI_SEMANTIC)",
            "contact.email -> emailAddress: FLATTEN (95% · AI_SEMANTIC)",
            "total_amount -> totalAmount: RENAME (95% · NORMALISED)",
        ],
        header_color=OT_NAVY, title_size=11.5, body_size=9.5,
    )
    add_card(
        s11, 8.80, 1.50, 3.85, 4.45,
        "System B: Cloud ERP Validated Output",
        [
            "orderId: 'ORD-2026-991' (camelCase contract)",
            "customerId: 'CUST-4081' (canonical ID)",
            "userName: 'Dr. Sarah Connor' (mapped synonym)",
            "dateOfBirth: '1988-08-15' (ISO-8601 date verified)",
            "accountEnabled: true (native JSON boolean)",
            "membershipLevel: 'GLD' (3-letter ERP enum code)",
            "emailAddress: 'sarah.c@cyberdyne.org' (flattened root field)",
            "totalAmount: 499.99 (JSON Schema VALIDATED)",
        ],
        header_color=OT_TEAL, title_size=11.5, body_size=9.8,
    )
    add_banner(
        s11, 0.67, 6.15, 11.98, 0.65,
        "Reverse READ Flow: When System A queries System B, SchemaBridge inverts all 8 rules in <3ms (re-nesting contact.email & restoring '15/08/1988').",
        bg_color=OT_BLUE,
    )

    # =========================================================================
    # SLIDE 12: Schema Drift Detection, Versioning & Security Guardrails
    # =========================================================================
    s12 = prs.slides.add_slide(L_TITLE_BODY)
    set_title(s12, "Schema Drift Detection, Versioning & Security Guardrails")
    remove_placeholder(s12, 1)

    add_card(
        s12, 0.67, 1.50, 3.85, 2.55,
        "Closed-Loop Schema Drift Detection",
        [
            "SHA-256 Structural Diffing: Detects ADDED, REMOVED, and TYPE_CHANGED fields across schema versions (/schemas/diff).",
            "Automatic Rule Impact Mapping: Cross-references schema diffs against active MappingRules and flags broken paths (BROKEN_PATH).",
            "Proactive Alerting: Catches breaking upstream/downstream API changes before bad data reaches production.",
        ],
        header_color=OT_NAVY, title_size=12, body_size=9.8,
    )
    add_card(
        s12, 4.73, 1.50, 3.85, 2.55,
        "Immutable Versioning & Approval Gates",
        [
            "Strict State Machine: Mappings progress through DRAFT -> SUGGESTED -> APPROVED -> PUBLISHED.",
            "Tamper-Proof Revisions: Modifying any rule on an approved mapping clears approval metadata and reverts to DRAFT.",
            "Read-Only Published Contracts: Published versions (v1..vN) are immutable; engineers clone a version to evolve rules safely.",
        ],
        header_color=OT_BLUE, title_size=12, body_size=9.8,
    )
    add_card(
        s12, 8.80, 1.50, 3.85, 2.55,
        "Enterprise Audit Trail & Observability",
        [
            "Full Lifecycle Auditing: Records every project creation, schema import, AI generation, rule edit, approval, and publication.",
            "MDC Correlation Logging: Structured JSON logs stamp every request with severity, serviceCode, and request.uuid.",
            "Execution Metrics: Tracks exact transformation duration (ms) and validation pass/fail rates per version.",
        ],
        header_color=OT_TEAL, title_size=12, body_size=9.8,
    )

    add_card(
        s12, 0.67, 4.25, 11.98, 2.60,
        "Defense-in-Depth Security & AI Prompt Safety Architecture",
        [
            "Zero Production Data Exposure: Customer payloads are processed strictly inside the local Spring Boot JVM; external AI never sees live transaction data.",
            "Prompt Injection Sandboxing: Aviator ADT system prompts treat all schema field names, descriptions, and sample values as untrusted data and enforce strict JSON Schema responses.",
            "Externalized Credential Hygiene: Google Vertex AI / Aviator credentials are injected via environment variables (credentials/credentials.json excluded from Git and logs).",
        ],
        header_color=OT_DARK_NAVY, title_size=12, body_size=10,
    )

    # =========================================================================
    # SLIDE 13: Patent-Pending Innovations (5 Core Claims)
    # =========================================================================
    s13 = prs.slides.add_slide(L_TITLE_BODY)
    set_title(s13, "Patent Disclosure: 5 Core Architectural Claims")
    remove_placeholder(s13, 1)

    claims = [
        (
            "CLAIM 1: 5-Tier Residual Matching Cascade",
            "Cascades field resolution through Exact, Normalised, Domain Synonym, and Historical rules at zero token cost — delegating ONLY residual unmapped fields to an LLM under strict schema constraints.",
            OT_NAVY,
        ),
        (
            "CLAIM 2: Decoupled AI-Advisory Synthesis with Zero-AI Runtime",
            "Isolates generative AI strictly to offline design-time rule discovery while executing 100% of live production transactions in a <5ms deterministic in-memory engine with zero PII exposure.",
            OT_BLUE,
        ),
        (
            "CLAIM 3: Closed-Loop Cryptographic Schema Drift & Impact Mapping",
            "Fingerprints schemas via SHA-256, computes structural tree diffs (added/removed/type-changed nodes), and automatically traces breaking changes to affected transformation rules (BROKEN_PATH).",
            OT_DARK_NAVY,
        ),
        (
            "CLAIM 4: Confidence-Gated Human-in-the-Loop Safeguard",
            "Computes multi-tier confidence scores for every rule, quarantining low-confidence or AI-inferred mappings into a mandatory review gate and blocking unapproved/unpublished rules from production.",
            OT_TEAL,
        ),
        (
            "CLAIM 5: Automated Bi-Directional Schema Inversion",
            "Automatically synthesizes reverse READ transformation rules (including NEST <-> FLATTEN and format reversal) from approved forward WRITE rules without writing integration logic twice.",
            OT_AMBER,
        ),
    ]
    for idx, (c_title, c_desc, c_col) in enumerate(claims):
        y = 1.48 + idx * 1.06
        add_card(
            s13, 0.67, y, 11.98, 0.96,
            c_title,
            [c_desc],
            header_color=c_col, title_size=11.5, body_size=10,
        )

    # =========================================================================
    # SLIDE 14: Business & Engineering Value Canvas (Matching Slide 13 of Template)
    # =========================================================================
    s14 = prs.slides.add_slide(L_TITLE_BODY)
    set_title(s14, "Business & Engineering Value Canvas")
    remove_placeholder(s14, 1)

    meta_items = [
        ("Designed for:", "Enterprise Integration & Platform Teams"),
        ("Inventor / Built by:", "Baljinder Singh"),
        ("AI Engine:", "OpenText Aviator ADT"),
        ("Runtime:", "Java 21 · Spring Boot 3 (<5ms)"),
    ]
    for i, (k, v) in enumerate(meta_items):
        add_pill_box(
            s14, 0.67 + i * 3.04, 1.32, 2.86, 0.48,
            f"{k} {v}", "", fill_color=OT_LIGHT_GRAY, text_color=OT_DARK_NAVY, title_size=9.5,
        )

    canvas_cols = [
        (
            "1. Key Partners & Inputs",
            [
                "OpenText Aviator ADT: Enterprise LLM orchestration & NL rule parsing.",
                "Source Systems (System A): Legacy POS, HR, Billing, On-Premise DBs.",
                "Target Systems (System B): Cloud ERP, Salesforce, ServiceNow, Directory APIs.",
            ],
            OT_NAVY,
        ),
        (
            "2. Key Activities",
            [
                "Schema Ingestion: Parse JSON Schemas, OpenAPI specs & sample payloads.",
                "5-Level Matching: Auto-map fields across deterministic + AI tiers.",
                "Deterministic Execution: Run bi-directional transformations in <5ms.",
            ],
            OT_BLUE,
        ),
        (
            "3. Value Propositions",
            [
                "90%+ Faster Onboarding: Map enterprise schemas in minutes, not weeks.",
                "100% Data Privacy: Zero live customer PII ever sent to an LLM.",
                "Ultra-Fast & Predictable: <5ms pure-Java execution with 0 hallucinations.",
            ],
            OT_DARK_NAVY,
        ),
        (
            "4. User Workflows",
            [
                "3-Column Visual Studio: Inspect, edit, and approve field mappings.",
                "NL Rule Assistant: Describe complex transformations in plain English.",
                "Transformation Lab: Live preview & JSON Schema validation before publish.",
            ],
            OT_BLUE,
        ),
        (
            "5. Target Teams",
            [
                "Enterprise Integration Architects: Bridging legacy & cloud SaaS ecosystems.",
                "Platform & API Engineering: Building B2B customer onboarding connectors.",
                "Regulated Industries: Healthcare (HIPAA), Finance (PCI), EU (GDPR).",
            ],
            OT_NAVY,
        ),
    ]
    for i, (c_title, c_bullets, c_col) in enumerate(canvas_cols):
        add_card(
            s14, 0.67 + i * 2.43, 1.92, 2.31, 3.35,
            c_title, c_bullets, header_color=c_col, title_size=10.5, body_size=8.8,
        )

    add_card(
        s14, 0.67, 5.40, 5.85, 1.55,
        "6. Token & Cost Efficiency Structure",
        [
            "Zero Runtime Token Cost: Millions of daily production API calls run on local CPU in pure Java at $0 LLM cost.",
            "80%+ Design-Time Token Savings: Levels 1–4 resolve exact, normalized, synonym, and historical fields before calling Aviator AI.",
        ],
        header_color=OT_SLATE, title_size=11, body_size=9.2,
    )
    add_card(
        s14, 6.80, 5.40, 5.85, 1.55,
        "7. Measurable Engineering ROI & Reliability Impact",
        [
            "Eliminates Silent Production Outages: SHA-256 schema drift detection and strict target JSON Schema validation catch breaking changes immediately.",
            "Halves Integration Maintenance: Automated bi-directional inversion maintains both WRITE and READ pipelines from a single rule set.",
        ],
        header_color=OT_TEAL, title_size=11, body_size=9.2,
    )

    # =========================================================================
    # SLIDE 15: Technology Stack, Deployment & Future Roadmap
    # =========================================================================
    s15 = prs.slides.add_slide(L_TITLE_BODY)
    set_title(s15, "Technology Stack, Deployment & Roadmap")
    remove_placeholder(s15, 1)

    roadmap_cols = [
        (
            "Enterprise Tech Stack",
            [
                "Backend: Java 21 LTS, Spring Boot 3.3.4, Spring Data JPA, Jackson AST, JSON Schema Validator, OpenAPI 2.6.",
                "Frontend: Angular 21 Standalone, Modern Dark Glassmorphism UI.",
                "AI Engine: OpenText Aviator ADT (Google GenAI / Vertex AI).",
                "Database & Infra: PostgreSQL / H2 zero-config dev, Redis, Docker Compose, Playwright E2E.",
            ],
            OT_NAVY,
        ),
        (
            "Q1–Q2: Core Platform (Completed)",
            [
                "5-Level Residual Matching Cascade & 20 deterministic Java operations.",
                "OpenText Aviator ADT integration for semantic matching & NL rules.",
                "3-Column Mapping Workspace, Transformation Lab & Bi-Directional Proxy.",
                "Lifecycle hardening, SHA-256 drift detection, Audit Trail & Patent Disclosure.",
            ],
            OT_TEAL,
        ),
        (
            "Q3: Event Streaming & Security",
            [
                "Kafka & RabbitMQ Connectors: High-throughput asynchronous event stream translation between topics.",
                "Enterprise RBAC & SSO: OAuth2 / OIDC project-level authorization and multi-tenant isolation.",
                "Optimistic Concurrency & Immutable Artifacts: Signed, exportable mapping bundles for air-gapped prod.",
            ],
            OT_BLUE,
        ),
        (
            "Q4: Multi-Protocol & Auto-Migration",
            [
                "GraphQL, SOAP/XML & Avro/Protobuf: Native multi-protocol ingestion and cross-protocol translation.",
                "AI Drift Auto-Patching: Aviator AI automatically proposes draft rule migrations when SHA-256 drift is detected.",
                "Live Traffic Shadow Testing: Replay production traffic against draft vNext mappings to verify 100% compatibility.",
            ],
            OT_DARK_NAVY,
        ),
    ]
    for i, (rq_title, rq_bullets, rq_col) in enumerate(roadmap_cols):
        add_card(
            s15, 0.67 + i * 3.04, 1.55, 2.86, 5.20,
            rq_title, rq_bullets, header_color=rq_col, title_size=11, body_size=9.4,
        )

    # =========================================================================
    # SLIDE 16: Back Cover (Layout: Back cover)
    # =========================================================================
    prs.slides.add_slide(L_BACK)

    prs.save(str(OUTPUT_PPTX))
    return OUTPUT_PPTX


if __name__ == "__main__":
    out = build_presentation()
    print(f"Saved presentation to: {out}")
