/** @type {import('tailwindcss').Config} */
export default {
  content: ["./index.html", "./src/**/*.{vue,js,ts,jsx,tsx}"],
  darkMode: ["class", '[data-theme="dark"]'],
  theme: {
    extend: {
      fontFamily: {
        sans: ["system-ui", "sans-serif"],
      },
      colors: {
        accent: {
          DEFAULT: "#5b7c32",
          hover: "#6d8f3d",
          muted: "rgba(91,124,50,0.15)",
        },
      },
    },
  },
  plugins: [],
};
