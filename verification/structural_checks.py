#!/usr/bin/env python3
"""Offline structural checks for the Left Android project (no SDK/Gradle required).
Run: python3 verification/structural_checks.py   (set LEFT_ROOT to override project path)
1. Required files exist  2. XML well-formed  3. No Float/Double money fields
4. TransactionDao capabilities (master prompt §12)  5. All use cases (§14)  6. Brace balance
"""
import os, re, sys
import xml.etree.ElementTree as ET

ROOT = os.environ.get("LEFT_ROOT", "/data/Left")
failures = []
checked = 0

def check(name, ok):
    global checked
    checked += 1
    if not ok:
        failures.append(name)
        print(f"FAIL: {name}")

REQUIRED = [
    "settings.gradle.kts", "build.gradle.kts", "gradle.properties",
    "gradle/libs.versions.toml", "gradle/wrapper/gradle-wrapper.properties",
    ".gitignore", "README.md", "ARCHITECTURE.md", "DATABASE.md", "DEVELOPMENT.md",
    ".github/workflows/android-ci.yml",
    "app/build.gradle.kts", "app/proguard-rules.pro",
    "app/src/main/AndroidManifest.xml",
    "app/src/main/res/values/strings.xml",
    "app/src/main/res/values/themes.xml",
    "app/src/main/res/values/colors.xml",
    "app/src/main/res/drawable/ic_launcher_foreground.xml",
    "app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml",
    "app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml",
    "app/src/main/java/com/left/app/LeftApplication.kt",
    "app/src/main/java/com/left/app/MainActivity.kt",
    "app/src/main/java/com/left/app/core/utils/Money.kt",
    "app/src/main/java/com/left/app/core/utils/CurrencyUtils.kt",
    "app/src/main/java/com/left/app/core/common/MonthRange.kt",
    "app/src/main/java/com/left/app/core/common/UiState.kt",
    "app/src/main/java/com/left/app/core/security/SafeLogger.kt",
    "app/src/main/java/com/left/app/core/network/NetworkMonitor.kt",
    "app/src/main/java/com/left/app/core/designsystem/Color.kt",
    "app/src/main/java/com/left/app/core/designsystem/Type.kt",
    "app/src/main/java/com/left/app/core/designsystem/Tokens.kt",
    "app/src/main/java/com/left/app/core/designsystem/Icon.kt",
    "app/src/main/java/com/left/app/core/designsystem/theme/LeftTheme.kt",
    "app/src/main/java/com/left/app/core/designsystem/component/LeftButton.kt",
    "app/src/main/java/com/left/app/core/designsystem/component/LeftTextField.kt",
    "app/src/main/java/com/left/app/core/designsystem/component/LeftCard.kt",
    "app/src/main/java/com/left/app/core/navigation/LeftNavHost.kt",
    "app/src/main/java/com/left/app/core/database/LeftDatabase.kt",
    "app/src/main/java/com/left/app/core/database/Converters.kt",
    "app/src/main/java/com/left/app/core/database/entity/UserProfileEntity.kt",
    "app/src/main/java/com/left/app/core/database/entity/CategoryEntity.kt",
    "app/src/main/java/com/left/app/core/database/entity/TransactionEntity.kt",
    "app/src/main/java/com/left/app/core/database/entity/SubscriptionEntity.kt",
    "app/src/main/java/com/left/app/core/database/entity/IncomeSourceEntity.kt",
    "app/src/main/java/com/left/app/core/database/entity/MonthlyBudgetEntity.kt",
    "app/src/main/java/com/left/app/core/database/dao/UserProfileDao.kt",
    "app/src/main/java/com/left/app/core/database/dao/CategoryDao.kt",
    "app/src/main/java/com/left/app/core/database/dao/TransactionDao.kt",
    "app/src/main/java/com/left/app/core/database/dao/SubscriptionDao.kt",
    "app/src/main/java/com/left/app/core/database/dao/IncomeSourceDao.kt",
    "app/src/main/java/com/left/app/core/database/dao/MonthlyBudgetDao.kt",
    "app/src/main/java/com/left/app/core/database/seed/DefaultCategories.kt",
    "app/src/main/java/com/left/app/core/database/seed/DatabaseSeeder.kt",
    "app/src/main/java/com/left/app/core/model/Transaction.kt",
    "app/src/main/java/com/left/app/core/model/Category.kt",
    "app/src/main/java/com/left/app/core/model/UserProfile.kt",
    "app/src/main/java/com/left/app/core/model/Subscription.kt",
    "app/src/main/java/com/left/app/core/model/IncomeSource.kt",
    "app/src/main/java/com/left/app/core/model/MonthlyBudget.kt",
    "app/src/main/java/com/left/app/core/data/UserProfileRepository.kt",
    "app/src/main/java/com/left/app/core/data/CategoryRepository.kt",
    "app/src/main/java/com/left/app/core/data/TransactionRepository.kt",
    "app/src/main/java/com/left/app/core/data/SubscriptionRepository.kt",
    "app/src/main/java/com/left/app/core/data/IncomeSourceRepository.kt",
    "app/src/main/java/com/left/app/core/data/MonthlyBudgetRepository.kt",
    "app/src/main/java/com/left/app/core/domain/TransactionUseCases.kt",
    "app/src/main/java/com/left/app/core/domain/CalculationUseCases.kt",
    "app/src/main/java/com/left/app/core/datastore/UserPreferencesDataStore.kt",
    "app/src/main/java/com/left/app/core/di/AppModule.kt",
    "app/src/main/java/com/left/app/core/di/DatabaseModule.kt",
    "app/src/main/java/com/left/app/core/di/DataStoreModule.kt",
    "app/src/main/java/com/left/app/core/di/RepositoryModule.kt",
    "app/src/main/java/com/left/app/feature/splash/SplashScreen.kt",
    "app/src/main/java/com/left/app/feature/onboarding/OnboardingScreen.kt",
    "app/src/main/java/com/left/app/feature/dashboard/DashboardScreen.kt",
    "app/src/main/java/com/left/app/feature/transactions/TransactionsScreen.kt",
    "app/src/main/java/com/left/app/feature/transactions/AddTransactionScreen.kt",
    "app/src/main/java/com/left/app/feature/analytics/AnalyticsScreen.kt",
    "app/src/main/java/com/left/app/feature/settings/SettingsScreen.kt",
    "app/src/test/java/com/left/app/core/utils/MoneyTest.kt",
    "app/src/test/java/com/left/app/core/common/MonthRangeTest.kt",
    "app/src/test/java/com/left/app/core/domain/TransactionUseCasesTest.kt",
    "app/src/test/java/com/left/app/core/domain/CalculationUseCasesTest.kt",
    "app/src/test/java/com/left/app/core/database/seed/DefaultCategoriesTest.kt",
    "app/src/test/java/com/left/app/core/data/fake/FakeRepositories.kt",
    "app/src/androidTest/java/com/left/app/core/database/TransactionDaoTest.kt",
    "app/src/androidTest/java/com/left/app/core/database/DatabaseSeedTest.kt",
    # Phase 2 — onboarding
    "app/src/main/java/com/left/app/core/domain/OnboardingUseCases.kt",
    "app/src/main/java/com/left/app/core/datastore/OnboardingPreferences.kt",
    "app/src/main/java/com/left/app/core/di/OnboardingPreferencesModule.kt",
    "app/src/main/java/com/left/app/feature/onboarding/OnboardingViewModel.kt",
    "app/src/main/java/com/left/app/feature/onboarding/OnboardingScreen.kt",
    "app/src/main/java/com/left/app/feature/onboarding/OnboardingSteps.kt",
    "app/src/test/java/com/left/app/core/domain/CompleteOnboardingTest.kt",
    "app/src/test/java/com/left/app/core/data/fake/FakeOnboardingRepositories.kt",
    "app/src/test/java/com/left/app/feature/onboarding/OnboardingViewModelTest.kt",
    "verification/OnboardingHarness.java",
]
for rel in REQUIRED:
    check(f"file exists: {rel}", os.path.isfile(os.path.join(ROOT, rel)))

for dirpath, _, files in os.walk(ROOT):
    for f in files:
        if f.endswith(".xml"):
            path = os.path.join(dirpath, f)
            try:
                ET.parse(path)
                check(f"xml well-formed: {os.path.relpath(path, ROOT)}", True)
            except ET.ParseError as e:
                check(f"xml well-formed: {os.path.relpath(path, ROOT)} ({e})", False)

MONEY_SCAN_DIRS = ["core/database", "core/model", "core/data", "core/utils"]
ALLOWED_DOUBLE_FILES = {"CalculationUseCases.kt"}  # derived percentage only
for sub in MONEY_SCAN_DIRS:
    base = os.path.join(ROOT, "app/src/main/java/com/left/app", sub)
    for dirpath, _, files in os.walk(base):
        for f in files:
            if not f.endswith(".kt"):
                continue
            src = open(os.path.join(dirpath, f)).read()
            src = re.sub(r"/\*\*.*?\*/", "", src, flags=re.S)  # KDoc
            src = re.sub(r"/\*.*?\*/", "", src, flags=re.S)    # block comments
            for i, line in enumerate(src.splitlines(), 1):
                stripped = line.split("//")[0]
                if re.search(r"\b(Float|Double)\b", stripped):
                    allowed = f in ALLOWED_DOUBLE_FILES and "Double" in stripped
                    check(f"no Float/Double money: {f}:{i} -> {line.strip()}", allowed)

dao_src = open(os.path.join(ROOT, "app/src/main/java/com/left/app/core/database/dao/TransactionDao.kt")).read()
for capability in ["insert", "update", "delete", "getById", "getAll", "getByMonth",
                   "getByDateRange", "getByCategory", "search", "observeRecent",
                   "observeMonthlyTotals"]:
    check(f"TransactionDao supports {capability}",
          re.search(r"fun\s+" + capability + r"\s*\(", dao_src) is not None)

domain = open(os.path.join(ROOT, "app/src/main/java/com/left/app/core/domain/TransactionUseCases.kt")).read()
domain += open(os.path.join(ROOT, "app/src/main/java/com/left/app/core/domain/CalculationUseCases.kt")).read()
for uc in ["AddTransaction", "UpdateTransaction", "DeleteTransaction", "GetTransaction",
           "GetTransactions", "GetMonthlyTransactions", "CalculateMonthlyIncome",
           "CalculateMonthlyExpenses", "CalculateRemainingMoney",
           "CalculateBudgetRemaining", "CalculateBudgetUsagePercentage"]:
    check(f"use case exists: {uc}", re.search(r"class\s+" + uc + r"\b", domain) is not None)

kt_count = 0
for dirpath, _, files in os.walk(ROOT):
    for f in files:
        if f.endswith(".kt") or f.endswith(".kts"):
            kt_count += 1
            src = open(os.path.join(dirpath, f)).read()
            src = re.sub(r'""".*?"""', '""', src, flags=re.S)
            src = re.sub(r'"(?:[^"\\]|\\.)*"', '""', src)
            src = re.sub(r"//.*", "", src)
            check(f"braces balanced: {f}", src.count("{") == src.count("}"))

print()
print(f"Structural checks run: {checked} ({kt_count} Kotlin files scanned)")
if failures:
    print(f"FAILURES: {len(failures)}")
    sys.exit(1)
print("ALL STRUCTURAL CHECKS PASSED")
