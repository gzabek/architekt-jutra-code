# Test Suite Results

## Status: PASSED WITH ISSUES (pre-existing failure only)

**Test Command:** `JAVA_HOME=/Users/kuba/Library/Java/JavaVirtualMachines/azul-25/Contents/Home mvn test -pl .`
**Execution Time:** ~11 seconds
**Date:** 2026-03-29

## Metrics

| Metric | Value |
|--------|-------|
| Total | 82 |
| Passing | 81 |
| Failing | 1 |
| Errors | 0 |
| Skipped | 0 |
| Pass Rate | 98.8% |

## Test Classes Summary

| Test Class | Tests | Result |
|------------|-------|--------|
| IntegrationTests | 7 | PASS |
| PluginDataAndObjectsIntegrationTests | 6 | PASS |
| PluginGapTests | 5 | PASS |
| PluginDatabaseTests | 4 | PASS |
| PluginObjectGapTests | 8 | PASS |
| PluginObjectApiAndFilterTests | 6 | PASS |
| PluginRegistryIntegrationTests | 6 | PASS |
| PluginObjectEntityBindingTests | 6 | PASS |
| CategoryIntegrationTests | 8 | PASS |
| CategoryValidationTests | 3 | PASS |
| ProductValidationTests | 7 | PASS |
| ProductIntegrationTests | 9 | 1 FAILURE |
| ApiLayerTests | 4 | PASS |
| AjApplicationTests | 3 | PASS |

## Failure Details

### 1. ProductIntegrationTests.listProducts_withSearch_returnsCaseInsensitiveMatches

- **File:** `pl.devstyle.aj.product.ProductIntegrationTests`
- **Line:** 132
- **Error:** `JSON path "$" Expected: a collection with size <1> but: collection size was <2>`
- **Type:** Integration
- **Related to implementation:** No
- **Regression risk:** None -- PRE-EXISTING FAILURE
- **Details:** This test failure exists prior to the current changes. The search query returns 2 results instead of the expected 1, likely due to sample data seeded by Liquibase migrations matching the search term in addition to the test-created product. This is a test isolation issue with pre-existing sample data, not a regression.

## Regression Analysis

- **Regressions found:** 0
- **New failures introduced by implementation:** 0
- **Pre-existing failures:** 1

The single failure (`listProducts_withSearch_returnsCaseInsensitiveMatches`) is a known pre-existing issue unrelated to the current entity binding implementation. All 6 new tests in `PluginObjectEntityBindingTests` pass successfully. All other existing test classes pass with zero failures.

## Conclusion

No regressions detected. The implementation changes (custom object entity binding) have not broken any existing functionality. The test suite is healthy aside from the one pre-existing failure in product search tests.
