#include <common/result_store.h>

#include <chrono>
#include <cstdlib>
#include <filesystem>
#include <fstream>
#include <iostream>
#include <string>

namespace fs = std::filesystem;

namespace {

[[noreturn]] void fail(const char *expression, const char *file, int line)
{
    std::cerr << file << ':' << line << ": check failed: " << expression << '\n';
    std::exit(EXIT_FAILURE);
}

#define CHECK(expression) do { if (!(expression)) fail(#expression, __FILE__, __LINE__); } while (false)

fs::path tempFile(const char *extension)
{
    const auto nonce = std::chrono::steady_clock::now().time_since_epoch().count();
    return fs::temp_directory_path() / ("clpeak-result-store-" + std::to_string(nonce) + extension);
}

ResultStore fixture()
{
    return {
        {"CUDA", "NVIDIA \"Platform\"", "GPU, 0", "550.1", "fp_compute", "single_precision", "float", "gflops", ResultStatus::Ok, 12345.678f, ""},
        {"CUDA", "NVIDIA \"Platform\"", "GPU, 0", "550.1", "fp_compute", "single_precision", "double", "gflops", ResultStatus::Unsupported, 0.0f, "not supported & unavailable"},
        {"CPU", "native", "CPU", "", "latency", "memory", "random", "us", ResultStatus::Ok, 42.25f, ""},
    };
}

void assertSame(const ResultStore &expected, const ResultStore &actual)
{
    CHECK(expected.size() == actual.size());
    for (size_t i = 0; i < expected.size(); ++i)
    {
        const ResultEntry &a = expected[i];
        const ResultEntry &b = actual[i];
        CHECK(a.backend == b.backend);
        CHECK(a.platform == b.platform);
        CHECK(a.device == b.device);
        CHECK(a.driver == b.driver);
        CHECK(a.category == b.category);
        CHECK(a.test == b.test);
        CHECK(a.metric == b.metric);
        CHECK(a.unit == b.unit);
        CHECK(a.status == b.status);
        CHECK(a.reason == b.reason);
        if (a.status == ResultStatus::Ok)
            CHECK(a.value == b.value);
    }
}

void testBaseline()
{
    const ResultStore rows = fixture();
    const BaselineMap baseline = buildBaselineMap(rows);
    CHECK(baseline.size() == 2);
    CHECK(baseline.at(rows[0].key()) == rows[0].value);
    CHECK(baseline.at(rows[2].key()) == rows[2].value);
    CHECK(baseline.find(rows[1].key()) == baseline.end());
}

void testRoundTrips()
{
    const ResultStore rows = fixture();

    const fs::path json = tempFile(".json");
    const bool jsonSaved = saveJson(rows, json.string());
    CHECK(jsonSaved);
    assertSame(rows, loadJson(json.string()));
    assertSame(rows, loadResultFile(json.string()));
    fs::remove(json);

    const fs::path csv = tempFile(".csv");
    const bool csvSaved = saveCsv(rows, csv.string());
    CHECK(csvSaved);
    assertSame(rows, loadCsv(csv.string()));
    assertSame(rows, loadResultFile(csv.string()));
    fs::remove(csv);

    const fs::path xml = tempFile(".xml");
    const bool xmlSaved = saveXml(rows, xml.string());
    CHECK(xmlSaved);
    assertSame(rows, loadXml(xml.string()));
    assertSame(rows, loadResultFile(xml.string()));
    fs::remove(xml);
}

void testRejectsLegacy()
{
    const fs::path json = tempFile(".json");
    {
        std::ofstream out(json);
        out << "[{\"backend\":\"CPU\",\"test\":\"old\",\"metric\":\"x\"}]\n";
    }
    CHECK(loadJson(json.string()).empty());
    fs::remove(json);
}

void testCategoryMapping()
{
    CHECK(categoryFromUnit("gflops") == Category::FpCompute);
    CHECK(categoryFromUnit("tops") == Category::IntCompute);
    CHECK(categoryFromUnit("gbps") == Category::Bandwidth);
    CHECK(categoryFromUnit("us") == Category::Latency);
    CHECK(categoryFromUnit("unknown") == Category::Unknown);
}
} // namespace

int main()
{
    testBaseline();
    testRoundTrips();
    testRejectsLegacy();
    testCategoryMapping();
    std::cout << "result_store_test: passed\n";
    return EXIT_SUCCESS;
}
