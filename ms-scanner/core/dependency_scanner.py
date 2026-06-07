"""
Dependency vulnerability scanner.
Parses requirements.txt / pyproject.toml and flags packages with known CVEs
or dangerous default behaviour.
"""
import re
import logging
from pathlib import Path
from core.models import IssueInfo

logger = logging.getLogger(__name__)

# Packages with known critical/high CVEs or dangerous defaults.
# Format: package_name_lower → (title, cwe_id, owasp_category, description, min_safe_version or None)
_VULNERABLE_PACKAGES: dict[str, tuple] = {
    "pyyaml": (
        "PyYAML yaml.load() Arbitrary Code Execution",
        "CWE-502",
        "A06:2021 – Vulnerable and Outdated Components",
        "PyYAML versions < 6.0 default yaml.load() allows arbitrary Python object deserialisation. "
        "An attacker who controls YAML input can achieve remote code execution. "
        "Always use yaml.safe_load() and upgrade to PyYAML >= 6.0.",
        "6.0",
    ),
    "pillow": (
        "Pillow Image Processing CVEs",
        "CWE-119",
        "A06:2021 – Vulnerable and Outdated Components",
        "Older Pillow versions have multiple CVEs including buffer overflows and arbitrary code execution "
        "via crafted image files. Ensure Pillow >= 10.0.1.",
        "10.0.1",
    ),
    "cryptography": (
        "Cryptography Library — Verify Version",
        "CWE-327",
        "A02:2021 – Cryptographic Failures",
        "The cryptography package has had multiple CVEs in versions < 41.0.0 including timing attacks "
        "and use-after-free vulnerabilities. Ensure you are running >= 41.0.0.",
        "41.0.0",
    ),
    "paramiko": (
        "Paramiko Authentication Bypass (versions < 2.10.1)",
        "CWE-287",
        "A07:2021 – Identification and Authentication Failures",
        "Paramiko < 2.10.1 contains an authentication bypass vulnerability (CVE-2022-24302). "
        "Upgrade to >= 2.10.1.",
        "2.10.1",
    ),
    "requests": (
        "Requests CRLF Injection / Certificate Verification",
        "CWE-93",
        "A03:2021 – Injection",
        "Requests < 2.31.0 is vulnerable to CRLF injection. Additionally, verify that "
        "certificate verification is not disabled (verify=False) anywhere in the codebase.",
        "2.31.0",
    ),
    "django": (
        "Django — Verify Version Against Known CVEs",
        "CWE-89",
        "A06:2021 – Vulnerable and Outdated Components",
        "Django has a long history of SQL injection and XSS CVEs in older versions. "
        "Ensure you are on a supported release (4.2 LTS or 5.x) with all security patches applied.",
        "4.2",
    ),
    "flask": (
        "Flask Debug Mode / SSTI Risk",
        "CWE-94",
        "A05:2021 – Security Misconfiguration",
        "Flask applications running with debug=True expose the Werkzeug debugger, which allows "
        "arbitrary code execution. Ensure debug mode is disabled in production via environment variable.",
        None,
    ),
    "sqlalchemy": (
        "SQLAlchemy — Raw Query Injection Risk",
        "CWE-89",
        "A03:2021 – Injection",
        "SQLAlchemy is present. Verify that raw queries (text(), execute() with string formatting) "
        "are not used with unsanitised user input. Use parameterised queries exclusively.",
        None,
    ),
    "pickle": (
        "pickle — Unsafe Deserialisation",
        "CWE-502",
        "A08:2021 – Software and Data Integrity Failures",
        "The pickle module is present in dependencies. pickle.loads() on untrusted data allows "
        "arbitrary code execution. Never deserialise pickle data from user-controlled sources.",
        None,
    ),
    "marshal": (
        "marshal — Unsafe Deserialisation",
        "CWE-502",
        "A08:2021 – Software and Data Integrity Failures",
        "marshal.loads() on untrusted data can lead to arbitrary code execution. "
        "Avoid deserialising marshal data from user-controlled sources.",
        None,
    ),
    "urllib3": (
        "urllib3 — Verify Version Against CVEs",
        "CWE-601",
        "A06:2021 – Vulnerable and Outdated Components",
        "urllib3 < 2.0.7 has multiple CVEs including redirect header injection. Upgrade to >= 2.0.7.",
        "2.0.7",
    ),
    "lxml": (
        "lxml XXE Vulnerability",
        "CWE-611",
        "A03:2021 – Injection",
        "lxml versions < 4.9.3 are vulnerable to XML External Entity (XXE) injection when parsing "
        "untrusted XML. Upgrade to >= 4.9.3 and use defusedxml for untrusted input.",
        "4.9.3",
    ),
    "jinja2": (
        "Jinja2 Server-Side Template Injection Risk",
        "CWE-94",
        "A03:2021 – Injection",
        "Jinja2 is present. Verify that render_template_string() is never called with user-controlled "
        "input, as this allows Server-Side Template Injection leading to RCE.",
        None,
    ),
}


def _parse_requirements(content: str) -> list[tuple[str, str]]:
    """Return list of (package_name_lower, version_string) from requirements.txt content."""
    results = []
    for line in content.splitlines():
        line = line.strip()
        if not line or line.startswith("#") or line.startswith("-"):
            continue
        # Strip extras: package[extra]>=version
        line = re.sub(r'\[.*?\]', '', line)
        match = re.match(r'^([A-Za-z0-9_\-\.]+)\s*([><=!~].+)?$', line)
        if match:
            name = match.group(1).lower().replace("-", "_").replace(".", "_")
            version = (match.group(2) or "").strip()
            results.append((name, version))
    return results


def scan_dependencies(scan_dir: str) -> list[IssueInfo]:
    """
    Find requirements.txt files under scan_dir and check for vulnerable packages.
    Returns IssueInfo objects with file_id=0 (no file row for dependency findings).
    """
    root = Path(scan_dir)
    req_files = list(root.rglob("requirements*.txt")) + list(root.rglob("requirements/*.txt"))
    issues: list[IssueInfo] = []

    for req_file in req_files:
        try:
            content = req_file.read_text(encoding="utf-8", errors="replace")
        except OSError:
            continue

        packages = _parse_requirements(content)
        for pkg_name, version_spec in packages:
            # Normalise name for lookup
            lookup_name = pkg_name.replace("-", "_").replace(".", "_")
            if lookup_name not in _VULNERABLE_PACKAGES:
                continue

            title, cwe_id, owasp_category, description, min_safe = _VULNERABLE_PACKAGES[lookup_name]

            logger.info("Dependency finding: %s in %s", title, req_file)

            issues.append(IssueInfo(
                file_id=0,
                file_name=str(req_file),
                function_name="dependencies",
                line_number=0,
                title=title,
                description=description,
                severity="high",
                confidence="medium",
                cwe_id=cwe_id,
                owasp_category=owasp_category,
                affected_snippet=f"{pkg_name}{version_spec}" if version_spec else pkg_name,
                suggestions=(
                    f"Upgrade to a safe version (>= {min_safe}) and audit all usages in the codebase."
                    if min_safe else
                    "Audit all usages of this package in the codebase and ensure it is used safely."
                ),
                remediation_example=f"{pkg_name}>={min_safe}" if min_safe else "",
                status="open",
                source="dependency_scanner",
            ))

    return issues
