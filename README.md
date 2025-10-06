# BuildDoctor 🔍

AI-powered CI/CD log analyzer that automatically diagnoses build failures and suggests fixes using GPT-3.5.

## Features

- 📊 Parses logs from TeamCity, GitHub Actions, Jenkins, and generic CI/CD systems
- 🤖 GPT-3.5 powered root cause analysis
- ⚡ REST API for programmatic access
- 📈 Extracts steps, errors, warnings, and performance metrics
- 🎯 Provides actionable fix recommendations

## Tech Stack

**Backend:** Kotlin, Ktor  
**AI:** OpenAI GPT-3.5 Turbo API  
**Build:** Gradle  
**Parsing:** Regex-based multi-format log extraction

## Quick Start

### Prerequisites

- JDK 17+
- OpenAI API key ([get one here](https://platform.openai.com/api-keys))

### Installation
```bash
# Clone the repository
git clone <your-repo-url>
cd BuildDoctor

# Set your OpenAI API key
export OPENAI_API_KEY="sk-your-key-here"

# Build and run
./gradlew run
