export default {
  transform: {
    "^.+\\.tsx?$": [
      "ts-jest",
      {
        // The package itself builds as ESM (module NodeNext), which ts-jest cannot run.
        // Tests therefore compile to CommonJS with the same strictness.
        tsconfig: {
          module: "commonjs",
          moduleResolution: "node10",
          target: "es2020",
          strict: true,
          esModuleInterop: true,
          types: ["jest", "node"],
        },
      },
    ],
  },
  testEnvironment: "node",
};
