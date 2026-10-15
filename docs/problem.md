# Stock Price Tracker

## Problem Statement

You are building a service to analyze intraday stock price data. Your task is to process a file containing real-time stock price updates and identify the highest and lowest prices observed for each stock symbol during the trading day.

This task mirrors a real-world scenario in financial analysis where traders and analysts need to quickly identify daily price ranges for multiple securities.

## Guidelines on AI Tool Usage

You are welcome to use AI tools (e.g., ChatGPT, GitHub Copilot, Claude) to assist with your implementation. However, you should be prepared to explain and justify every significant decision made in your code, including:
- Architectural choices and trade-offs
- Library or framework selections
- Design patterns and their rationale
- How you handle edge cases
- Performance considerations

During the discussion, we expect you to articulate *why* each approach was chosen, not just *what* the code does. If AI generated code, you should understand it thoroughly and be able to explain the reasoning behind it.

## Requirements

### Input
You will be provided with a CSV file containing stock price updates. Each record contains:
- `stockSymbol`: A string representing the stock's ticker symbol (e.g., "AAPL", "TSLA")
- `price`: A decimal number representing the stock price in USD with 6 decimal places (e.g., 150.250000)
- `timestamp`: An ISO 8601 formatted timestamp indicating when the price update occurred (e.g., "2026-09-16T09:30:00Z")
- `exchange`: The stock exchange where the trade occurred (e.g., "NASDAQ")
- `volume`: (Optional) The number of shares traded

### Processing Logic
For each stock symbol in the input data:
- Track the highest price observed
- Track the lowest price observed
- Record the timestamp when each high/low occurred

### Output
Produce output containing the daily high/low summary for each stock symbol:
- `stockSymbol`: The ticker symbol
- `highPrice`: The highest price observed for this stock in USD (6 decimal places)
- `highTimestamp`: The ISO 8601 formatted timestamp when the high occurred
- `lowPrice`: The lowest price observed for this stock in USD (6 decimal places)
- `lowTimestamp`: The ISO 8601 formatted timestamp when the low occurred

## Input Example (CSV)
```
stockSymbol,price,timestamp,exchange,volume
AAPL,173.478878,2026-09-16T14:39:32.983730Z,AMEX,181072
MSFT,400.036885,2026-09-16T14:39:32.983730Z,NASDAQ,2091940
GOOGL,152.653252,2026-09-16T14:39:32.983730Z,AMEX,3812999
AMZN,201.069641,2026-09-16T14:39:32.983730Z,NYSE,3859710
TSLA,264.674211,2026-09-16T14:39:32.983730Z,NYSE,2735089
META,483.991864,2026-09-16T14:39:32.983730Z,AMEX,3102625
NVDA,126.675177,2026-09-16T14:39:32.983730Z,AMEX,2010260
JPM,183.218063,2026-09-16T14:39:32.983730Z,NYSE,1509676
V,280.276892,2026-09-16T14:39:32.983730Z,NYSE,809673
JNJ,155.715137,2026-09-16T14:39:32.983730Z,NYSE,3942635
```

## Expected Output Format

When exposing results via an API endpoint or similar mechanism, the response should follow this JSON format:

```json
[
  {
    "stockSymbol": "MSFT",
    "highPrice": 420.000001,
    "highTimestamp": "2026-09-16T12:33:23.902691Z",
    "lowPrice": 380.010004,
    "lowTimestamp": "2026-09-16T12:50:42.902691Z"
  },
  {
    "stockSymbol": "NVDA",
    "highPrice": 140.000002,
    "highTimestamp": "2026-09-16T12:46:46.902691Z",
    "lowPrice": 120.010009,
    "lowTimestamp": "2026-09-16T12:00:53.902691Z"
  }
]
```

**Note:** The exact values in your output will differ based on the input data file provided to you. The format above shows the expected structure and data types. All prices are in USD with 6 decimal places of precision.

**Implementation approach:** The JSON format above shows the expected structure for results. You have flexibility in how you design your solution:
- You may read directly from the provided file and produce output immediately
- You may build a more sophisticated architecture with intermediate processing stages
- Your choice of technology, frameworks, and architectural patterns should align with your approach and justify the added complexity

## Constraints & Considerations
- Handle malformed or missing fields gracefully and document your choice for handling them. 
- Ensure the solution correctly identifies highs and lows even with out-of-order input
- The input file contains overlapping price data for 10 different tickers
- Your implementation should be reasonably efficient for the given dataset

## Assumptions
- All timestamps are in the same trading day
- You have access to the input file locally
- No external persistent database is required (though you may use one if you prefer)

## Deliverables
1. **Service/Application**: A working solution that:
   - Reads and processes the provided input file
   - Produces the high/low price summary for each stock symbol in the specified JSON format
   - Is built using your technology stack of choice

2. **Documentation (README.md)**:
   - How to build and run your application
   - Your architectural approach and design decisions
   - Why you chose your technology stack and frameworks
   - How you handled edge cases and data validation

## Evaluation Criteria
We're interested in:
- **Correctness**: Does your solution accurately identify highs and lows?
- **Approach**: What methodology did you choose and why?
- **Code quality**: Is the code clean, readable, and maintainable?
- **Edge case handling**: How do you handle malformed data or empty inputs?
- **Documentation**: Can you clearly explain your design decisions?

## Optional Enhancements (Not Required)
- Expose a REST API to query results
- Persist results to a database
- Add unit tests for your logic
- Process data from a Kafka topic instead of a file
- Handle multiple trading days with daily resets
- Add performance metrics or logging
