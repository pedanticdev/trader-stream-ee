#!/bin/bash

# TradeStreamEE Test Execution Script
# Provides different test execution profiles for comprehensive testing

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Function to print colored output
print_status() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Function to check if a command exists
command_exists() {
    command -v "$1" >/dev/null 2>&1
}

# Check prerequisites
check_prerequisites() {
    print_status "Checking prerequisites..."

    if ! command_exists java; then
        print_error "Java is not installed or not in PATH"
        exit 1
    fi

    # Check for maven wrapper or installed maven
    if [ ! -x "./mvnw" ] && ! command_exists mvn; then
        print_error "Maven wrapper (mvnw) not found/executable and 'mvn' not in PATH"
        exit 1
    fi

    # Robust Java version check
    local java_version=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}')
    local major_version=$(echo "$java_version" | awk -F. '{if ($1 == 1) print $2; else print $1}')
    
    if [[ "$major_version" -lt 17 ]]; then
        print_warning "Java 17+ recommended. Current version: $java_version"
    fi

    print_success "Prerequisites check completed"
}

# Clean previous test results
clean_test_results() {
    print_status "Cleaning previous test results..."
    ./mvnw clean -q
    rm -rf target/site/jacoco/
    rm -rf target/surefire-reports/
    rm -rf target/failsafe-reports/
    print_success "Test results cleaned"
}

# Run unit tests
run_unit_tests() {
    print_status "Running unit tests..."

    echo "Compiling and running unit tests..."
    if ./mvnw test -q; then
        print_success "Unit tests completed successfully"

        # Show test summary if available
        if [ -f "target/surefire-reports/TEST-fish.payara.trader.TestRunner.xml" ]; then
            local test_count=$(grep -o 'tests="[0-9]*"' target/surefire-reports/*.xml | head -1 | grep -o '[0-9]*')
            local failure_count=$(grep -o 'failures="[0-9]*"' target/surefire-reports/*.xml | head -1 | grep -o '[0-9]*')
            local error_count=$(grep -o 'errors="[0-9]*"' target/surefire-reports/*.xml | head -1 | grep -o '[0-9]*')

            echo "Test Summary: $test_count tests, $failure_count failures, $error_count errors"

            if [[ "$failure_count" -eq "0" && "$error_count" -eq "0" ]]; then
                print_success "All unit tests passed!"
            else
                print_warning "Some unit tests failed or had errors"
            fi
        fi
    else
        print_error "Unit tests failed"
        return 1
    fi
}

# Run integration tests
run_integration_tests() {
    print_status "Running integration tests..."

    echo "Compiling and running integration tests..."
    if ./mvnw verify -Pintegration-tests -q; then
        print_success "Integration tests completed successfully"
    else
        print_error "Integration tests failed"
        return 1
    fi
}

# Generate coverage report
generate_coverage_report() {
    print_status "Generating test coverage report..."

    if ./mvnw jacoco:report -q; then
        print_success "Coverage report generated"

        if [ -f "target/site/jacoco/index.html" ]; then
            # Extract total coverage percentage more reliably
            local instruction_coverage=$(grep -A1 "Total" target/site/jacoco/index.html | grep -oE '[0-9]+%' | head -1)
            echo "Instruction Coverage: ${instruction_coverage:-N/A}"

            # Open coverage report in browser if available
            if command_exists xdg-open; then
                print_status "Opening coverage report in browser..."
                xdg-open target/site/jacoco/index.html
            elif command_exists open; then
                print_status "Opening coverage report in browser..."
                open target/site/jacoco/index.html
            else
                print_status "Coverage report available at: target/site/jacoco/index.html"
            fi
        fi
    else
        print_warning "Failed to generate coverage report"
    fi
}

# Run performance benchmarks
run_benchmarks() {
    print_status "Running JMH performance benchmarks..."

    echo "This may take several minutes..."
    if ./mvnw exec:java@run-benchmarks -q; then
        print_success "Benchmarks completed successfully"
    else
        print_warning "Benchmarks failed or were interrupted"
    fi
}

# Run load tests
run_load_tests() {
    print_status "Running load tests..."

    echo "This may take several minutes..."
    if ./mvnw failsafe:integration-test -Dtest="LoadTest" -q; then
        print_success "Load tests completed successfully"
    else
        print_warning "Load tests failed or were interrupted"
    fi
}

# Quick test run (unit tests only)
quick_test() {
    print_status "Running quick test (unit tests only)..."
    clean_test_results
    run_unit_tests
    generate_coverage_report
}

# Full test suite
full_test_suite() {
    print_status "Running full test suite..."

    clean_test_results

    # Run unit tests
    if run_unit_tests; then
        # Run integration tests
        if run_integration_tests; then
            # Generate coverage report
            generate_coverage_report

            # Optional: run benchmarks
            if [[ "$SKIP_BENCHMARKS" != "true" ]]; then
                read -p "Run performance benchmarks? (y/N): " -n 1 -r
                echo
                if [[ $REPLY =~ ^[Yy]$ ]]; then
                    run_benchmarks
                fi
            fi

            # Optional: run load tests
            if [[ "$SKIP_LOAD_TESTS" != "true" ]]; then
                read -p "Run load tests? (y/N): " -n 1 -r
                echo
                if [[ $REPLY =~ ^[Yy]$ ]]; then
                    run_load_tests
                fi
            fi

            print_success "Full test suite completed successfully!"
        else
            print_error "Integration tests failed"
            return 1
        fi
    else
        print_error "Unit tests failed"
        return 1
    fi
}

# Show test results summary
show_results() {
    print_status "Test Results Summary:"

    echo ""
    echo "Reports available:"
    if [ -f "target/site/jacoco/index.html" ]; then
        echo "  • Coverage Report: target/site/jacoco/index.html"
    fi

    if [ -d "target/surefire-reports" ]; then
        echo "  • Unit Test Reports: target/surefire-reports/"
    fi

    if [ -d "target/failsafe-reports" ]; then
        echo "  • Integration Test Reports: target/failsafe-reports/"
    fi

    echo ""
}

# Help function
show_help() {
    echo "TradeStreamEE Test Execution Script"
    echo ""
    echo "Usage: $0 [OPTION]"
    echo ""
    echo "Options:"
    echo "  quick          Run quick unit tests only"
    echo "  full           Run full test suite (unit + integration)"
    echo "  unit           Run unit tests"
    echo "  integration    Run integration tests"
    echo "  coverage       Generate coverage report only"
    echo "  benchmarks     Run JMH performance benchmarks"
    echo "  load           Run load tests"
    echo "  clean          Clean test results"
    echo "  results        Show test results summary"
    echo "  help           Show this help message"
    echo ""
    echo "Environment Variables:"
    echo "  SKIP_BENCHMARKS=true   Skip running benchmarks in full suite"
    echo "  SKIP_LOAD_TESTS=true     Skip running load tests in full suite"
    echo ""
}

# Main script logic
main() {
    echo "========================================="
    echo "  TradeStreamEE Test Runner"
    echo "========================================="
    echo ""

    check_prerequisites

    case "${1:-help}" in
        "quick")
            quick_test
            show_results
            ;;
        "full")
            full_test_suite
            show_results
            ;;
        "unit")
            clean_test_results
            run_unit_tests
            show_results
            ;;
        "integration")
            clean_test_results
            run_integration_tests
            show_results
            ;;
        "coverage")
            generate_coverage_report
            ;;
        "benchmarks")
            run_benchmarks
            ;;
        "load")
            clean_test_results
            run_load_tests
            show_results
            ;;
        "clean")
            clean_test_results
            ;;
        "results")
            show_results
            ;;
        "help"|*)
            show_help
            ;;
    esac
}

# Run main function with all arguments
main "$@"