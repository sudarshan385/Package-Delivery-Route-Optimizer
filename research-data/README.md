# Amazon Last Mile Routing Research Dataset

The benchmark data files for the DDQN-PPDRP research module (`package_data.json`, `route_data.json`, `actual_sequences.json`) originate from the Amazon Last Mile Routing Research Challenge.

Due to GitHub's 100 MB per-file size limit (`package_data.json` is ~358 MB, `route_data.json` is ~75 MB), the raw benchmark JSON files are excluded from Git version control.

## Usage
1. Download the Amazon Last Mile Routing Challenge dataset files.
2. Place `package_data.json`, `route_data.json`, and `actual_sequences.json` directly into this `research-data/` directory.
3. Unit and integration tests (`AmazonDryRunValidationTest`, `AmazonDatasetProviderTest`, `DatasetValidatorTest`) run automatically using isolated test samples.
