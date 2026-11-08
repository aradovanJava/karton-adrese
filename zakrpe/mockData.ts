export type Profile =
  | "Općenito"
  | "Obitelj s djecom"
  | "Student"
  | "Umirovljenik"
  | "Osoba s invaliditetom";

/** Četiri stanja pristupačnosti: "nema podatka" nikad se ne prikazuje kao "da" ni kao "ne". */
export type AccessState = "da" | "djelomično" | "ne" | "nema podatka";

export type Interest = "Bicikl" | "Knjižnica" | "Sport" | "Menza";
export type Need = "Kolica" | "Otežano hodanje" | "Slabovidnost";

/** Udaljenosti su zračne (ravna crta od adrese do objekta), u metrima. */
export interface Place {
  name: string;
  distanceM: number;
  source: string;
  detail?: string;
  wheelchair?: AccessState;
}

export interface Faculty {
  id: string;
  name: string;
  address: string;
  coordinates: [number, number];
  /** Linije ZET-a na stajalištima do 400 m od ustanove (ZET GTFS). */
  lines: string[];
}

export interface Karton {
  address: string;
  coordinates: [number, number];
  district: string;
  localBoard: string;
  school: Place & {
    shifts: number;
    afterSchoolCare: boolean;
    sportsHall: boolean;
    pupils: number;
  };
  kindergarten: Place & { freePlaces: boolean; inEnrolmentArea: boolean };
  transit: Place & { lines: string[]; direction: string; departures: string[] };
  healthCentre: Place;
  pharmacy: Place;
  airStation: Place & { pm10AnnualAverage: number; daysAboveLimit: number };
  accessibility: {
    facilities: Place[];
    pedestrianZone: Place;
    /** ZET GTFS ne objavljuje polje wheelchair_boarding – stanje je "nema podatka". */
    stopAccess: AccessState;
  };
  earthquakeAssembly: Place;
  publicSpaces: {
    playground: Place & { kind: string };
    park: Place & { inside: boolean };
    sport: Place & { kind: string };
    odmorko: Place & { activity: string; schoolYear: string };
    cityGarden: Place & { plots: number };
  };
  student: {
    menza: Place;
    housing: Place;
    library: Place;
    bikeLane: Place;
    publicBike: Place & { racks: number };
    sport: Place;
  };
  services15: { score: number; total: number; checklist: { name: string; available: boolean }[] };
  populationDensity: number;
  facts: { text: string; source: string }[];
}

export const profiles: Profile[] = [
  "Općenito",
  "Obitelj s djecom",
  "Student",
  "Umirovljenik",
  "Osoba s invaliditetom",
];

export const interestOptions: Interest[] = ["Menza", "Knjižnica", "Bicikl", "Sport"];
export const needOptions: Need[] = ["Kolica", "Otežano hodanje", "Slabovidnost"];

/** Prag indeksa 15 minuta: 15 min × 80 m/min ÷ faktor zaobilaženja 1,3 ≈ 920 m zračne udaljenosti. */
export const THRESHOLD_15_MIN_M = 920;

const fakSource = "Grad Zagreb – Geoportal visokoškolske ustanove";

/** Ustanove iz skupa Geoportal visokoškolske ustanove (koordinate stvarne, linije ogledne). */
export const faculties: Faculty[] = [
  {
    id: "efzg",
    name: "Ekonomski fakultet",
    address: "Trg J. F. Kennedyja 6",
    coordinates: [45.816007, 16.011504],
    lines: ["4", "7", "11", "12"],
  },
  {
    id: "fer",
    name: "Fakultet elektrotehnike i računarstva",
    address: "Unska ulica 3",
    coordinates: [45.801135, 15.970907],
    lines: ["4", "5", "13", "14", "17"],
  },
  {
    id: "ffzg",
    name: "Filozofski fakultet",
    address: "Ulica Ivana Lučića 3",
    coordinates: [45.796588, 15.971307],
    lines: ["3", "5", "13", "14"],
  },
  {
    id: "pmf",
    name: "Prirodoslovno-matematički fakultet",
    address: "Horvatovac 102A",
    coordinates: [45.826122, 15.988867],
    lines: ["105", "226"],
  },
  {
    id: "tvz",
    name: "Tehničko veleučilište u Zagrebu",
    address: "Vrbik 8",
    coordinates: [45.795461, 15.969957],
    lines: ["5", "7", "13", "14"],
  },
  {
    id: "mef",
    name: "Medicinski fakultet",
    address: "Šalata 3B",
    coordinates: [45.818974, 15.985061],
    lines: ["105"],
  },
];

export const exampleAddresses = [
  "Gorjanovićeva ulica 15",
  "Ilica 1",
  "Trg bana Josipa Jelačića 5",
  "Savska cesta 25",
  "Maksimirska cesta 100",
];

const source = "Grad Zagreb · 12. 10. 2026.";
const portal = (name: string) => `Grad Zagreb – ${name} (data.zagreb.hr)`;

export const kartoni: Record<string, Karton> = {
  "Gorjanovićeva ulica 15": {
    address: "Gorjanovićeva ulica 15",
    coordinates: [45.822021, 16.006415],
    district: "Gradska četvrt Maksimir",
    localBoard: "Mjesni odbor Bukovac",
    school: {
      name: "Osnovna škola Vladimira Nazora",
      distanceM: 260,
      source: portal("Geoportal Osnovne škole"),
      shifts: 2,
      afterSchoolCare: true,
      sportsHall: true,
      pupils: 478,
      wheelchair: "nema podatka",
    },
    kindergarten: {
      name: "Dječji vrtić Bukovac",
      distanceM: 229,
      source,
      freePlaces: true,
      inEnrolmentArea: true,
    },
    transit: {
      name: "Jordanovac – sjever",
      distanceM: 497,
      source: "ZET GTFS · 12. 10. 2026.",
      lines: ["4", "5", "7", "11", "12", "34"],
      direction: "Kvaternikov trg",
      departures: ["2 min", "8 min", "14 min"],
    },
    healthCentre: {
      name: "Dom zdravlja Zagreb – Istok",
      distanceM: 850,
      source,
      wheelchair: "da",
    },
    pharmacy: {
      name: "Ljekarna Bukovac",
      distanceM: 620,
      source: portal("Geoportal Ljekarne"),
      wheelchair: "djelomično",
    },
    airStation: {
      name: "Đorđićeva ulica",
      distanceM: 2440,
      source: "Grad Zagreb – mreža za praćenje kvalitete zraka",
      pm10AnnualAverage: 18,
      daysAboveLimit: 12,
    },
    accessibility: {
      facilities: [
        {
          name: "Centar za odgoj i obrazovanje „Vinko Bek”",
          distanceM: 1087,
          source: portal("Geoportal Ustanove za osobe s invaliditetom"),
          detail: "Dom za osobe s tjelesnim, intelektualnim i osjetilnim oštećenjima",
        },
        {
          name: "URIHO – profesionalna rehabilitacija i zapošljavanje",
          distanceM: 1555,
          source: portal("Geoportal Ustanove za osobe s invaliditetom"),
          detail: "Radna priprema i zapošljavanje osoba s invaliditetom",
        },
      ],
      pedestrianZone: {
        name: "Pješačka zona – središte grada",
        distanceM: 2380,
        source: portal("Geoportal pješačka zona"),
      },
      stopAccess: "nema podatka",
    },
    earthquakeAssembly: {
      name: "Park Maksimir – zapadni ulaz",
      distanceM: 710,
      source,
    },
    publicSpaces: {
      playground: {
        name: "Ružmarinka, iza k. br. 21",
        kind: "dječje igralište",
        distanceM: 571,
        source: portal("Geoportal javna sportska igrališta"),
      },
      park: {
        name: "Park Maksimir",
        distanceM: 640,
        inside: false,
        source: "OpenStreetMap (ODbL)",
      },
      sport: {
        name: "Mini sportsko-rekreacijski centar Barutanski breg",
        kind: "sportsko-rekreacijski centar",
        distanceM: 502,
        source: portal("Geoportal Sportski objekti"),
      },
      odmorko: {
        name: "ŠRC Šalata",
        activity: "klizanje",
        schoolYear: "2023./2024.",
        distanceM: 1759,
        source: portal("Geoportal Odmorko"),
      },
      cityGarden: {
        name: "Gradski vrt Mandlova",
        plots: 128,
        distanceM: 2111,
        source: portal("Geoportal gradski vrtovi"),
      },
    },
    student: {
      menza: {
        name: "Studentski restoran „Lašćina”",
        distanceM: 551,
        source: portal("Geoportal Studentski restoran"),
      },
      housing: {
        name: "Studentsko naselje „Lašćina”",
        distanceM: 551,
        source: portal("Geoportal Studentsko naselje"),
      },
      library: {
        name: "Knjižnice Grada Zagreba – Knjižnica Maksimir",
        distanceM: 940,
        source: portal("Knjižnice Grada Zagreba"),
      },
      bikeLane: {
        name: "Maksimirska cesta – jug",
        distanceM: 647,
        source: portal("Geoportal biciklističke staze"),
      },
      publicBike: {
        name: "Stanica javnih bicikala „Stadion Maksimir”",
        racks: 5,
        distanceM: 648,
        source: portal("Geoportal Sustav javnih bicikala"),
      },
      sport: {
        name: "Mini sportsko-rekreacijski centar Barutanski breg",
        distanceM: 502,
        source: portal("Geoportal Sportski objekti"),
      },
    },
    services15: {
      score: 5,
      total: 6,
      checklist: [
        { name: "Škola", available: true },
        { name: "Vrtić", available: true },
        { name: "Ljekarna", available: true },
        { name: "Dom zdravlja", available: true },
        { name: "Stajalište", available: true },
        { name: "Trgovina", available: false },
      ],
    },
    populationDensity: 8420,
    facts: [
      { text: "Najbliža osnovna škola radi u dvije smjene i ima produženi boravak.", source },
      { text: "U dječjem vrtiću Bukovac trenutno ima slobodnih mjesta.", source },
      { text: "Stajalište Jordanovac – sjever udaljeno je 500 m.", source },
    ],
  },
};

const illustrative: Omit<Karton, "address"> = {
  coordinates: [45.8131, 15.9775],
  district: "Gradska četvrt Donji grad",
  localBoard: "Mjesni odbor Centar",
  school: {
    name: "Osnovna škola u blizini",
    distanceM: 420,
    source,
    shifts: 1,
    afterSchoolCare: true,
    sportsHall: false,
    pupils: 390,
  },
  kindergarten: {
    name: "Dječji vrtić u blizini",
    distanceM: 350,
    source,
    freePlaces: false,
    inEnrolmentArea: true,
  },
  transit: {
    name: "Središnje stajalište",
    distanceM: 300,
    source,
    lines: ["6", "11", "12", "13", "14"],
    direction: "Trg bana Josipa Jelačića",
    departures: ["4 min", "11 min"],
  },
  healthCentre: { name: "Dom zdravlja u blizini", distanceM: 780, source, wheelchair: "nema podatka" },
  pharmacy: { name: "Ljekarna u blizini", distanceM: 280, source, wheelchair: "da" },
  airStation: {
    name: "Mjerna postaja Zagreb",
    distanceM: 1600,
    source,
    pm10AnnualAverage: 19,
    daysAboveLimit: 14,
  },
  accessibility: {
    facilities: [
      { name: "Ustanova za osobe s invaliditetom u blizini", distanceM: 640, source },
    ],
    pedestrianZone: { name: "Pješačka zona – središte grada", distanceM: 0, source },
    stopAccess: "nema podatka",
  },
  earthquakeAssembly: { name: "Javni park u blizini", distanceM: 520, source },
  publicSpaces: {
    playground: { name: "Dječje igralište u blizini", kind: "dječje igralište", distanceM: 310, source },
    park: { name: "Park u blizini", distanceM: 0, inside: true, source: "OpenStreetMap (ODbL)" },
    sport: { name: "Sportska dvorana u blizini", kind: "sportska dvorana", distanceM: 890, source },
    odmorko: {
      name: "Odmorko – sportska aktivnost",
      activity: "plivanje",
      schoolYear: "2023./2024.",
      distanceM: 2600,
      source,
    },
    cityGarden: { name: "Gradski vrt u blizini", plots: 93, distanceM: 2900, source },
  },
  student: {
    menza: { name: "Studentski restoran u blizini", distanceM: 700, source },
    housing: { name: "Studentsko naselje u blizini", distanceM: 2300, source },
    library: { name: "Knjižnica u blizini", distanceM: 380, source },
    bikeLane: { name: "Biciklistička staza u blizini", distanceM: 150, source },
    publicBike: { name: "Stanica javnih bicikala u blizini", racks: 10, distanceM: 260, source },
    sport: { name: "Sportski objekt u blizini", distanceM: 890, source },
  },
  services15: {
    score: 4,
    total: 6,
    checklist: [
      { name: "Škola", available: true },
      { name: "Vrtić", available: true },
      { name: "Ljekarna", available: true },
      { name: "Dom zdravlja", available: false },
      { name: "Stajalište", available: true },
      { name: "Trgovina", available: false },
    ],
  },
  populationDensity: 11200,
  facts: [
    { text: "Osnovne usluge nalaze se u krugu od 1 km.", source },
    { text: "Prikazani podaci temelje se na javnim izvorima.", source },
  ],
};

for (const address of exampleAddresses.slice(1)) {
  kartoni[address] = {
    ...illustrative,
    address,
    coordinates: [45.8131 + Math.random() * 0.02, 15.9775 + Math.random() * 0.03],
  };
}

export interface GridCell {
  id: string;
  coordinates: [number, number];
  bounds: [[number, number], [number, number]];
  index15: number;
  populationDensity: number;
  missingServices: string[];
}

const gridServices = ["Škola", "Vrtić", "Ljekarna", "Dom zdravlja", "Stajalište", "Trgovina"];
const gridRows = 10;
const gridColumns = 18;
const cellHeight = 0.009;
const cellWidth = 0.013;
const gridSouth = 45.755;
const gridWest = 15.86;

export const indexGrid: GridCell[] = Array.from({ length: gridRows * gridColumns }, (_, i) => {
  const row = Math.floor(i / gridColumns);
  const column = i % gridColumns;
  const south = gridSouth + row * cellHeight;
  const west = gridWest + column * cellWidth;
  // Ogledni raspored: dostupnost opada od središta (Trg bana Jelačića) prema periferiji
  const distanceKm = Math.hypot(row + 0.5 - 6.5, column + 0.5 - 9);
  const noise = (((i * 37) % 5) - 2) * 0.4;
  const hillside = row >= 9 ? 1.5 : 0;
  const index15 = Math.max(0, Math.min(6, Math.round(6.2 - distanceKm * 0.6 + noise - hillside)));

  return {
    id: `mreža-${row + 1}-${column + 1}`,
    coordinates: [south + cellHeight / 2, west + cellWidth / 2],
    bounds: [
      [south, west],
      [south + cellHeight, west + cellWidth],
    ],
    index15,
    populationDensity: Math.max(
      300,
      Math.round(
        (14500 - distanceKm * 1350 + (((i * 53) % 7) - 3) * 650 - hillside * 3000) / 50,
      ) * 50,
    ),
    missingServices: gridServices.slice(index15),
  };
});
