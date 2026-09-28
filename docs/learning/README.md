# Learning Neo4flix

This folder is a beginner-friendly book about the Neo4flix project. It starts
with the basic questions—what the application is, what a user can do with it,
and how to run it—before introducing services, APIs, Neo4j, Spring Boot,
Angular, authentication, recommendations, migrations, and tests.

You do not need to know Java, TypeScript, Docker, or graph databases before
starting. When a technical term first appears, the chapter explains it in
plain language. The [glossary](glossary.md) is available whenever a term is
unfamiliar.

## How to read this book

Read the chapters in numerical order on a first pass. Each chapter builds on
the ideas introduced before it and ends with a short recap. Code paths are
links to the real implementation, so you can move from an explanation to the
source code without searching the repository.

You can also use the guide as a reference:

| If you want to... | Read |
| --- | --- |
| Understand what the project does | [1. What is Neo4flix?](01-what-is-neo4flix.md) |
| Use the application as a normal user | [2. How a user uses it](02-how-a-user-uses-it.md) |
| See how all parts communicate | [3. How it works at a glance](03-how-it-works-at-a-glance.md) |
| Start the project on your computer | [4. Run the project](04-how-to-run-it.md) |
| Understand the four backend services | [5. Services explained](05-the-services-explained.md) |
| Learn why Neo4j is used | [6. The graph database](06-the-neo4j-graph.md) |
| Learn Spring Boot and its annotations | [7. The Java backend](07-the-java-backend.md) |
| Understand Angular components and routes | [8. The Angular frontend](08-the-angular-frontend.md) |
| Understand login, JWT, roles, and 2FA | [9. Authentication and security](09-login-security-and-2fa.md) |
| Understand the recommendation algorithm | [10. Recommendations](10-recommendations.md) |
| Understand migrations and seed data | [11. Data setup](11-data-migrations-and-audit-fixtures.md) |
| Run tests and prepare for the audit | [12. Testing](12-testing-the-project.md) |

## What you will understand by the end

After reading the book, you should be able to explain:

- what Neo4flix does and what it deliberately does not do;
- how a browser request travels through Angular, Nginx, a Spring Boot service,
  and Neo4j;
- why the backend is divided into User, Movie, Rating, and Recommendation
  services;
- how nodes and relationships represent users, movies, genres, ratings, and
  watchlists;
- what common Spring annotations such as `@RestController`, `@Service`,
  `@Repository`, and `@Transactional` mean;
- how Angular routes, components, services, signals, forms, and HTTP
  interceptors work together;
- how access tokens, refresh cookies, roles, password hashing, and TOTP 2FA
  protect the application;
- how Neo4flix creates and explains personalized recommendations;
- why migrations and deterministic seed data exist; and
- which tests check each layer of the system.

## A note about commands and secrets

The command examples assume Windows PowerShell 7 and that you are in the
repository root. Commands are shown as examples to type; passwords, private
keys, TOTP secrets, and tokens must never be copied into documentation or
committed to Git.

For the authoritative operational instructions, use
[Local development](../DEVELOPMENT.md). This book explains those instructions;
it does not replace them.

Start with [Chapter 1: What is Neo4flix?](01-what-is-neo4flix.md).
