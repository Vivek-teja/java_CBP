# =============================================================================
# Package Installation Script for Traffic Analytics R Visualization
# =============================================================================

required_packages <- c("DBI", "RMariaDB", "ggplot2", "dplyr", "tidyr", "scales")

cat("Checking required R packages for Traffic Analytics...\n")

missing_packages <- required_packages[!(required_packages %in% installed.packages()[,"Package"])]

if (length(missing_packages) > 0) {
  cat("Installing missing packages:", paste(missing_packages, collapse = ", "), "\n")
  install.packages(missing_packages, repos = "https://cloud.r-project.org")
} else {
  cat("All required R packages are already installed.\n")
}

# Verification
cat("\nVerification:\n")
for (pkg in required_packages) {
  loaded <- suppressWarnings(suppressMessages(require(pkg, character.only = TRUE)))
  cat(sprintf("  - %-12s : %s\n", pkg, ifelse(loaded, "SUCCESS", "FAILED")))
}
cat("\nSetup check complete.\n")
