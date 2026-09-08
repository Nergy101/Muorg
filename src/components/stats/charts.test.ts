import { describe, expect, it } from "vitest";
import { mount } from "@vue/test-utils";
import GenrePieChart from "./GenrePieChart.vue";
import RatingChart from "./RatingChart.vue";
import YearLineChart from "./YearLineChart.vue";
import type { CatalogTrack } from "@/types";

/**
 * The three sidebar charts. They are shared, so a break shows up in the desktop
 * app's Stats tab with no compile error — the bucketing and the SVG geometry
 * are just arithmetic that silently produces a wrong picture.
 */
function track(p: Partial<CatalogTrack> = {}): CatalogTrack {
  return {
    id: 1,
    path: "/m/a.mp3",
    root_id: 1,
    title: "t",
    artist: null,
    album: null,
    album_artist: null,
    featuring: null,
    year: null,
    genre: null,
    track_number: null,
    disc_number: null,
    duration_secs: null,
    format: "mp3",
    mtime_secs: 0,
    has_cover: false,
    rating: null,
    play_count: 0,
    last_played_at: null,
    ...p,
  };
}

const many = (n: number, p: Partial<CatalogTrack>) =>
  Array.from({ length: n }, (_, i) => track({ ...p, id: i }));

describe("RatingChart", () => {
  it("says so rather than drawing an empty chart", () => {
    const wrapper = mount(RatingChart, { props: { tracks: [] } });
    expect(wrapper.text()).toContain("No tracks.");
  });

  it("draws a bar per rating plus one for unrated", () => {
    const wrapper = mount(RatingChart, { props: { tracks: [track({ rating: 5 })] } });
    expect(wrapper.text()).toContain("★★★★★");
    expect(wrapper.text()).toContain("—");
  });

  it("gives the tallest bucket the full height and scales the rest to it", () => {
    const wrapper = mount(RatingChart, {
      props: { tracks: [...many(10, { rating: 5 }), ...many(5, { rating: 3 })] },
    });
    const heights = wrapper
      .findAll('[style*="height"]')
      .map((el) => parseFloat(/height:\s*([\d.]+)%/.exec(el.attributes("style") ?? "")?.[1] ?? "0"));

    expect(Math.max(...heights)).toBeCloseTo(100);
    expect(heights).toContain(50);
  });

  it("clamps an out-of-range rating into the 1-5 buckets", () => {
    // A rating of 9 in the database must not write past the end of the array.
    const wrapper = mount(RatingChart, {
      props: { tracks: [track({ rating: 9 }), track({ rating: 0 })] },
    });
    expect(wrapper.text()).not.toContain("NaN");
  });

  it("counts a null rating as unrated, not as zero stars", () => {
    const wrapper = mount(RatingChart, { props: { tracks: many(3, { rating: null }) } });
    // All three land in the unrated bucket, which is therefore the tallest.
    const heights = wrapper
      .findAll('[style*="height"]')
      .map((el) => parseFloat(/height:\s*([\d.]+)%/.exec(el.attributes("style") ?? "")?.[1] ?? "0"));
    expect(heights[0]).toBeCloseTo(100);
    expect(heights.slice(1).every((h) => h === 0)).toBe(true);
  });
});

describe("GenrePieChart", () => {
  it("renders nothing for an empty library", () => {
    const wrapper = mount(GenrePieChart, { props: { tracks: [] } });
    expect(wrapper.find("path").exists()).toBe(false);
  });

  it("labels untagged tracks as Unknown", () => {
    const wrapper = mount(GenrePieChart, {
      props: { tracks: [track({ genre: null }), track({ genre: "   " })] },
    });
    expect(wrapper.text()).toContain("Unknown");
  });

  it("orders slices by count, largest first", () => {
    const wrapper = mount(GenrePieChart, {
      props: {
        tracks: [...many(2, { genre: "Jazz" }), ...many(10, { genre: "Metal" })],
      },
    });
    const text = wrapper.text();
    expect(text.indexOf("Metal")).toBeLessThan(text.indexOf("Jazz"));
  });

  it("folds tiny slices past the sixth into Other", () => {
    // Without this the legend becomes a hundred one-track genres.
    const tracks = [
      ...many(100, { genre: "Metal" }),
      ...Array.from({ length: 10 }, (_, i) => track({ id: 200 + i, genre: `Rare ${i}` })),
    ];
    const wrapper = mount(GenrePieChart, { props: { tracks } });
    expect(wrapper.text()).toContain("Other");
  });

  it("keeps a small genre that lands in the first six", () => {
    const wrapper = mount(GenrePieChart, {
      props: { tracks: [...many(100, { genre: "Metal" }), track({ id: 999, genre: "Jazz" })] },
    });
    expect(wrapper.text()).toContain("Jazz");
    expect(wrapper.text()).not.toContain("Other");
  });

  it("draws one donut path per slice", () => {
    const wrapper = mount(GenrePieChart, {
      props: { tracks: [...many(5, { genre: "A" }), ...many(5, { genre: "B" })] },
    });
    const paths = wrapper.findAll("path");
    expect(paths.length).toBeGreaterThanOrEqual(2);
    for (const p of paths) {
      const d = p.attributes("d") ?? "";
      expect(d).not.toContain("NaN");
    }
  });

  it("draws a single genre without producing a degenerate arc", () => {
    // A 360° slice is the case where start and end angles coincide.
    const wrapper = mount(GenrePieChart, { props: { tracks: many(5, { genre: "Metal" }) } });
    const d = wrapper.find("path").attributes("d") ?? "";
    expect(d).not.toContain("NaN");
    expect(d.length).toBeGreaterThan(0);
  });
});

describe("YearLineChart", () => {
  it("says so when nothing has a usable year", () => {
    const wrapper = mount(YearLineChart, { props: { tracks: [track({ year: null })] } });
    expect(wrapper.text().toLowerCase()).toContain("no");
  });

  it("ignores implausible years rather than stretching the axis to them", () => {
    // A year of 0 or 3000 from a bad tag would otherwise flatten the whole
    // chart into one pixel at the far edge.
    const wrapper = mount(YearLineChart, {
      props: {
        tracks: [track({ id: 1, year: 1800 }), track({ id: 2, year: 3000 })],
      },
    });
    expect(wrapper.text().toLowerCase()).toContain("no");
  });

  it("plots a run of years without gaps in the axis", () => {
    const wrapper = mount(YearLineChart, {
      props: {
        tracks: [
          track({ id: 1, year: 1998 }),
          track({ id: 2, year: 2000 }), // 1999 has no tracks and must still exist
          track({ id: 3, year: 2000 }),
        ],
      },
    });
    const svg = wrapper.find("svg").html();
    expect(svg).not.toContain("NaN");
    expect(wrapper.text()).toContain("1998");
    expect(wrapper.text()).toContain("2000");
  });

  it("produces finite geometry for a single year", () => {
    // One year means a zero-width x range — the classic divide-by-zero.
    const wrapper = mount(YearLineChart, { props: { tracks: [track({ year: 1998 })] } });
    expect(wrapper.find("svg").html()).not.toContain("NaN");
  });
});
