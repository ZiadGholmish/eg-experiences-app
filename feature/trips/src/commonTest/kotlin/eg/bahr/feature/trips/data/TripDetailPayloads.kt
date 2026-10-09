package eg.bahr.feature.trips.data

/**
 * `GET /api/v1/trips/burullus-dawn` and `…/departures` as the backend serves the seeded trip
 * (bahr-be `scripts/seed-burullus.sql`), in both languages: the prototype's copy, four Saturdays
 * with 6, 2, 0 (sold out) and 11 seats left of 18. Null fields are omitted, as the backend omits them
 * (`shareUrl`). Images carry `variants` (390px avif/webp/jpeg) and the host has an avatar, as served since
 * the bahr-be `Image.variants` / `host.avatar` change. Dates are fixed to October 2026; the seed computes them from today.
 * LQIPs are shortened: the decode does not look inside them.
 */
internal object TripDetailPayloads {
    val english =
        """
{
 "success": true,
 "data": {
  "slug": "burullus-dawn",
  "title": "Dawn on Lake Burullus",
  "subtitle": "Lake Burullus · Kafr El Sheikh",
  "durationLabel": "05:00 → 22:00",
  "durationMinutes": 1020,
  "price": {
   "amount": 450,
   "currency": "EGP"
  },
  "categories": [
   "on_the_boat",
   "birds",
   "food"
  ],
  "heroImage": {
   "url": "http://localhost:9000/bahr-assets/seed/joy-boats.png",
   "width": 900,
   "height": 560,
   "alt": "Fishing boats on Lake Burullus",
   "lqip": "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAwAAAAHCAYAAAA8sqwkAAAA",
   "variants": [
    {
     "url": "http://localhost:9000/bahr-assets/seed/_v/joy-boats.png/390.avif",
     "width": 390,
     "format": "avif"
    },
    {
     "url": "http://localhost:9000/bahr-assets/seed/_v/joy-boats.png/390.webp",
     "width": 390,
     "format": "webp"
    },
    {
     "url": "http://localhost:9000/bahr-assets/seed/_v/joy-boats.png/390.jpeg",
     "width": 390,
     "format": "jpeg"
    }
   ]
  },
  "cardImage": {
   "url": "http://localhost:9000/bahr-assets/seed/card-dawn.png",
   "width": 600,
   "height": 600,
   "alt": "The lake at dawn",
   "lqip": "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAwAAAAHCAYAAAA8sqwkAAAA",
   "variants": [
    {
     "url": "http://localhost:9000/bahr-assets/seed/_v/card-dawn.png/390.avif",
     "width": 390,
     "format": "avif"
    },
    {
     "url": "http://localhost:9000/bahr-assets/seed/_v/card-dawn.png/390.webp",
     "width": 390,
     "format": "webp"
    },
    {
     "url": "http://localhost:9000/bahr-assets/seed/_v/card-dawn.png/390.jpeg",
     "width": 390,
     "format": "jpeg"
    }
   ]
  },
  "nextDeparture": {
   "date": "2026-10-10",
   "seatsRemaining": 6,
   "capacity": 18,
   "soldOut": false
  },
  "badge": {
   "label": "Most booked",
   "tone": "primary"
  },
  "rating": {
   "value": 4.8,
   "count": 37
  },
  "location": {
   "lat": 31.503,
   "lng": 30.804,
   "label": "Burg El Burullus"
  },
  "deck": "One day out of Cairo on Egypt's shallowest lagoon, in Ashraf's tear-drop fishing boat. Leaves 05:00, back 22:00.",
  "departure": {
   "placeName": "Abdel Moneim Riad",
   "city": "Cairo",
   "governorate": "Cairo",
   "lat": 30.0566,
   "lng": 31.2288,
   "timeLocal": "05:00",
   "arriveBy": "04:45"
  },
  "destination": {
   "placeName": "Burg El Burullus",
   "city": "Burg El Burullus",
   "governorate": "Kafr El Sheikh",
   "lat": 31.503,
   "lng": 30.804,
   "distanceKm": 150,
   "travelTime": "2h40"
  },
  "included": [
   "A/C bus, Cairo ⇄ Burullus",
   "Two boat runs on the lake",
   "Guide, Arabic and English",
   "Grilled mullet lunch",
   "Bottled water, 2 L",
   "Harbour entry fee"
  ],
  "excluded": [
   "Breakfast stop (≈40 EGP)",
   "Extra drinks and tea",
   "Tips for the crew",
   "Hotel pickup",
   "Travel insurance"
  ],
  "itinerary": [
   {
    "time": "05:00",
    "text": "Depart Cairo, Abdel Moneim Riad terminal",
    "icon": "directions_bus",
    "kind": "transit",
    "approximate": false
   },
   {
    "time": "07:40",
    "text": "Breakfast stop on the Tanta road — fuul, tea, toilets",
    "icon": "local_cafe",
    "kind": "meal",
    "approximate": false
   },
   {
    "time": "09:20",
    "text": "Arrive Burg El Burullus harbour, meet Ashraf and the boat",
    "icon": "sailing",
    "kind": "transit",
    "approximate": false
   },
   {
    "time": "09:45",
    "text": "First run: reed channels and the bird banks, motor off",
    "icon": "flutter_dash",
    "kind": "activity",
    "approximate": false
   },
   {
    "time": "12:15",
    "text": "Lunch at the fishermen's house — mullet grilled on the coals",
    "icon": "restaurant",
    "kind": "meal",
    "approximate": true
   },
   {
    "time": "14:00",
    "text": "Walk the murals of Burg El Burullus with the painters' families",
    "icon": "palette",
    "kind": "activity",
    "approximate": true
   },
   {
    "time": "15:30",
    "text": "Second run: out to the boughaz, where the lake meets the sea",
    "icon": "waves",
    "kind": "activity",
    "approximate": true
   },
   {
    "time": "17:30",
    "text": "Tea on the harbour wall, sunset over the reeds",
    "icon": "local_cafe",
    "kind": "activity",
    "approximate": true
   },
   {
    "time": "18:30",
    "text": "Leave for Cairo",
    "icon": "directions_bus",
    "kind": "transit",
    "approximate": true
   },
   {
    "time": "22:00",
    "text": "Back at Abdel Moneim Riad",
    "icon": "home",
    "kind": "transit",
    "approximate": true
   }
  ],
  "host": {
   "name": "Ashraf El Bahr",
   "avatar": {
    "url": "http://localhost:9000/bahr-assets/seed/host.png",
    "width": 240,
    "height": 240,
    "variants": []
   },
   "role": "Boat owner, Burg El Burullus",
   "tripsRun": 11,
   "verified": true,
   "phone": "+201000000101"
  },
  "tips": [
   {
    "key": "bring",
    "title": "What to bring",
    "body": "Cash — there is no ATM in the village. A hat, long sleeves, and shoes that can get wet. The boat has shade for about half the run."
   },
   {
    "key": "best_time",
    "title": "Best time of year",
    "body": "October to March for the birds; November is the peak. Avoid July and August midday — the lake gives no shelter from the sun."
   },
   {
    "key": "look_out",
    "title": "What to look out for",
    "body": "Flamingos on the north banks after the first cold week. Ashraf's hull — the tear-drop stern is only built here. The painted doors on Sharia El Bahr."
   },
   {
    "key": "know",
    "title": "What to know",
    "body": "The lake is knee-deep in most places, but there are no life jackets for children under 4. Toilets only at the breakfast stop and the fishermen's house."
   }
  ],
  "reviews": {
   "average": 4.8,
   "count": 37,
   "items": [
    {
     "author": "Nada H.",
     "dateISO": "2026-09-21",
     "body": "At 09:00 the lake is completely flat and silent, and Ashraf cuts the motor so you hear the birds. Bring a hat — I did not.",
     "partyLabel": "Went with 3 friends",
     "tone": "quaternary"
    },
    {
     "author": "Mahmoud A.",
     "dateISO": "2026-09-14",
     "body": "Long day, and we were back in Cairo at 22:40 not 22:00. Worth it for the mullet lunch alone.",
     "partyLabel": "Went as a couple",
     "tone": "tertiary"
    }
   ]
  },
  "dates": [
   {
    "id": "0199c3a0-5eed-7000-8000-000000000601",
    "date": "2026-10-10",
    "dayLabel": "Sat 10 Oct",
    "departTime": "05:00",
    "returnTime": "22:00",
    "seatsRemaining": 6,
    "capacity": 18,
    "soldOut": false,
    "bookable": true,
    "price": {
     "amount": 450,
     "currency": "EGP"
    }
   },
   {
    "id": "0199c3a0-5eed-7000-8000-000000000602",
    "date": "2026-10-17",
    "dayLabel": "Sat 17 Oct",
    "departTime": "05:00",
    "returnTime": "22:00",
    "seatsRemaining": 2,
    "capacity": 18,
    "soldOut": false,
    "bookable": true,
    "price": {
     "amount": 450,
     "currency": "EGP"
    }
   },
   {
    "id": "0199c3a0-5eed-7000-8000-000000000603",
    "date": "2026-10-24",
    "dayLabel": "Sat 24 Oct",
    "departTime": "05:00",
    "returnTime": "22:00",
    "seatsRemaining": 0,
    "capacity": 18,
    "soldOut": true,
    "bookable": false,
    "price": {
     "amount": 450,
     "currency": "EGP"
    }
   },
   {
    "id": "0199c3a0-5eed-7000-8000-000000000604",
    "date": "2026-10-31",
    "dayLabel": "Sat 31 Oct",
    "departTime": "05:00",
    "returnTime": "22:00",
    "seatsRemaining": 11,
    "capacity": 18,
    "soldOut": false,
    "bookable": true,
    "price": {
     "amount": 450,
     "currency": "EGP"
    }
   }
  ],
  "policy": {
   "freeCancellationHours": 72,
   "childFreeUnder": 6,
   "maxPartySize": 6
  },
  "gallery": [
   {
    "url": "http://localhost:9000/bahr-assets/seed/joy-boats.png",
    "width": 900,
    "height": 560,
    "alt": "Fishing boats on Lake Burullus",
    "lqip": "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAwAAAAHCAYAAAA8sqwkAAAA",
    "variants": [
     {
      "url": "http://localhost:9000/bahr-assets/seed/_v/joy-boats.png/390.avif",
      "width": 390,
      "format": "avif"
     },
     {
      "url": "http://localhost:9000/bahr-assets/seed/_v/joy-boats.png/390.webp",
      "width": 390,
      "format": "webp"
     },
     {
      "url": "http://localhost:9000/bahr-assets/seed/_v/joy-boats.png/390.jpeg",
      "width": 390,
      "format": "jpeg"
     }
    ]
   },
   {
    "url": "http://localhost:9000/bahr-assets/seed/card-dawn.png",
    "width": 600,
    "height": 600,
    "alt": "The lake at dawn",
    "lqip": "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAwAAAAHCAYAAAA8sqwkAAAA",
    "variants": [
     {
      "url": "http://localhost:9000/bahr-assets/seed/_v/card-dawn.png/390.avif",
      "width": 390,
      "format": "avif"
     },
     {
      "url": "http://localhost:9000/bahr-assets/seed/_v/card-dawn.png/390.webp",
      "width": 390,
      "format": "webp"
     },
     {
      "url": "http://localhost:9000/bahr-assets/seed/_v/card-dawn.png/390.jpeg",
      "width": 390,
      "format": "jpeg"
     }
    ]
   },
   {
    "url": "http://localhost:9000/bahr-assets/seed/joy-birds.png",
    "width": 900,
    "height": 560,
    "alt": "Birds on the lake banks",
    "lqip": "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAwAAAAHCAYAAAA8sqwkAAAA",
    "variants": [
     {
      "url": "http://localhost:9000/bahr-assets/seed/_v/joy-birds.png/390.avif",
      "width": 390,
      "format": "avif"
     },
     {
      "url": "http://localhost:9000/bahr-assets/seed/_v/joy-birds.png/390.webp",
      "width": 390,
      "format": "webp"
     },
     {
      "url": "http://localhost:9000/bahr-assets/seed/_v/joy-birds.png/390.jpeg",
      "width": 390,
      "format": "jpeg"
     }
    ]
   },
   {
    "url": "http://localhost:9000/bahr-assets/seed/joy-market.png",
    "width": 900,
    "height": 560,
    "alt": "The fish market at Burg El Burullus",
    "lqip": "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAwAAAAHCAYAAAA8sqwkAAAA",
    "variants": [
     {
      "url": "http://localhost:9000/bahr-assets/seed/_v/joy-market.png/390.avif",
      "width": 390,
      "format": "avif"
     },
     {
      "url": "http://localhost:9000/bahr-assets/seed/_v/joy-market.png/390.webp",
      "width": 390,
      "format": "webp"
     },
     {
      "url": "http://localhost:9000/bahr-assets/seed/_v/joy-market.png/390.jpeg",
      "width": 390,
      "format": "jpeg"
     }
    ]
   },
   {
    "url": "http://localhost:9000/bahr-assets/seed/joy-mural.png",
    "width": 900,
    "height": 560,
    "alt": "A painted house in Burg El Burullus",
    "lqip": "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAwAAAAHCAYAAAA8sqwkAAAA",
    "variants": [
     {
      "url": "http://localhost:9000/bahr-assets/seed/_v/joy-mural.png/390.avif",
      "width": 390,
      "format": "avif"
     },
     {
      "url": "http://localhost:9000/bahr-assets/seed/_v/joy-mural.png/390.webp",
      "width": 390,
      "format": "webp"
     },
     {
      "url": "http://localhost:9000/bahr-assets/seed/_v/joy-mural.png/390.jpeg",
      "width": 390,
      "format": "jpeg"
     }
    ]
   },
   {
    "url": "http://localhost:9000/bahr-assets/seed/joy-boats.png",
    "width": 900,
    "height": 560,
    "alt": "Fishing boats on Lake Burullus",
    "lqip": "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAwAAAAHCAYAAAA8sqwkAAAA",
    "variants": [
     {
      "url": "http://localhost:9000/bahr-assets/seed/_v/joy-boats.png/390.avif",
      "width": 390,
      "format": "avif"
     },
     {
      "url": "http://localhost:9000/bahr-assets/seed/_v/joy-boats.png/390.webp",
      "width": 390,
      "format": "webp"
     },
     {
      "url": "http://localhost:9000/bahr-assets/seed/_v/joy-boats.png/390.jpeg",
      "width": 390,
      "format": "jpeg"
     }
    ]
   }
  ],
  "og": {
   "image": {
    "url": "http://localhost:9000/bahr-assets/seed/joy-boats.png",
    "width": 900,
    "height": 560,
    "alt": "Fishing boats on Lake Burullus",
    "lqip": "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAwAAAAHCAYAAAA8sqwkAAAA",
    "variants": [
     {
      "url": "http://localhost:9000/bahr-assets/seed/_v/joy-boats.png/390.avif",
      "width": 390,
      "format": "avif"
     },
     {
      "url": "http://localhost:9000/bahr-assets/seed/_v/joy-boats.png/390.webp",
      "width": 390,
      "format": "webp"
     },
     {
      "url": "http://localhost:9000/bahr-assets/seed/_v/joy-boats.png/390.jpeg",
      "width": 390,
      "format": "jpeg"
     }
    ]
   },
   "title": "Dawn on Lake Burullus — 450 EGP, Cairo and back in one day",
   "description": "Leaves Abdel Moneim Riad 05:00, back 22:00. Bus, two boat runs, guide and a grilled mullet lunch included. Saturdays, 18 seats."
  }
 }
}
        """.trimIndent()

    val englishDepartures =
        """
{
 "success": true,
 "data": [
  {
   "id": "0199c3a0-5eed-7000-8000-000000000601",
   "date": "2026-10-10",
   "dayLabel": "Sat 10 Oct",
   "departTime": "05:00",
   "returnTime": "22:00",
   "seatsRemaining": 6,
   "capacity": 18,
   "soldOut": false,
   "bookable": true,
   "price": {
    "amount": 450,
    "currency": "EGP"
   }
  },
  {
   "id": "0199c3a0-5eed-7000-8000-000000000602",
   "date": "2026-10-17",
   "dayLabel": "Sat 17 Oct",
   "departTime": "05:00",
   "returnTime": "22:00",
   "seatsRemaining": 2,
   "capacity": 18,
   "soldOut": false,
   "bookable": true,
   "price": {
    "amount": 450,
    "currency": "EGP"
   }
  },
  {
   "id": "0199c3a0-5eed-7000-8000-000000000603",
   "date": "2026-10-24",
   "dayLabel": "Sat 24 Oct",
   "departTime": "05:00",
   "returnTime": "22:00",
   "seatsRemaining": 0,
   "capacity": 18,
   "soldOut": true,
   "bookable": false,
   "price": {
    "amount": 450,
    "currency": "EGP"
   }
  },
  {
   "id": "0199c3a0-5eed-7000-8000-000000000604",
   "date": "2026-10-31",
   "dayLabel": "Sat 31 Oct",
   "departTime": "05:00",
   "returnTime": "22:00",
   "seatsRemaining": 11,
   "capacity": 18,
   "soldOut": false,
   "bookable": true,
   "price": {
    "amount": 450,
    "currency": "EGP"
   }
  }
 ]
}
        """.trimIndent()

    val arabic =
        """
{
 "success": true,
 "data": {
  "slug": "burullus-dawn",
  "title": "الفجر على بحيرة البرلس",
  "subtitle": "بحيرة البرلس · كفر الشيخ",
  "durationLabel": "05:00 → 22:00",
  "durationMinutes": 1020,
  "price": {
   "amount": 450,
   "currency": "EGP"
  },
  "categories": [
   "on_the_boat",
   "birds",
   "food"
  ],
  "heroImage": {
   "url": "http://localhost:9000/bahr-assets/seed/joy-boats.png",
   "width": 900,
   "height": 560,
   "alt": "قوارب الصيد على بحيرة البرلس",
   "lqip": "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAwAAAAHCAYAAAA8sqwkAAAA",
   "variants": [
    {
     "url": "http://localhost:9000/bahr-assets/seed/_v/joy-boats.png/390.avif",
     "width": 390,
     "format": "avif"
    },
    {
     "url": "http://localhost:9000/bahr-assets/seed/_v/joy-boats.png/390.webp",
     "width": 390,
     "format": "webp"
    },
    {
     "url": "http://localhost:9000/bahr-assets/seed/_v/joy-boats.png/390.jpeg",
     "width": 390,
     "format": "jpeg"
    }
   ]
  },
  "cardImage": {
   "url": "http://localhost:9000/bahr-assets/seed/card-dawn.png",
   "width": 600,
   "height": 600,
   "alt": "البحيرة وقت الفجر",
   "lqip": "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAwAAAAHCAYAAAA8sqwkAAAA",
   "variants": [
    {
     "url": "http://localhost:9000/bahr-assets/seed/_v/card-dawn.png/390.avif",
     "width": 390,
     "format": "avif"
    },
    {
     "url": "http://localhost:9000/bahr-assets/seed/_v/card-dawn.png/390.webp",
     "width": 390,
     "format": "webp"
    },
    {
     "url": "http://localhost:9000/bahr-assets/seed/_v/card-dawn.png/390.jpeg",
     "width": 390,
     "format": "jpeg"
    }
   ]
  },
  "nextDeparture": {
   "date": "2026-10-10",
   "seatsRemaining": 6,
   "capacity": 18,
   "soldOut": false
  },
  "badge": {
   "label": "الأكثر حجزًا",
   "tone": "primary"
  },
  "rating": {
   "value": 4.8,
   "count": 37
  },
  "location": {
   "lat": 31.503,
   "lng": 30.804,
   "label": "برج البرلس"
  },
  "deck": "يوم واحد بعيد عن القاهرة على أكثر بحيرة ضحلة في مصر، مع قارب أشرف اللي شكله زي الدمعة. المغادرة 05:00 والرجوع 22:00.",
  "departure": {
   "placeName": "موقف عبد المنعم رياض",
   "city": "القاهرة",
   "governorate": "القاهرة",
   "lat": 30.0566,
   "lng": 31.2288,
   "timeLocal": "05:00",
   "arriveBy": "04:45"
  },
  "destination": {
   "placeName": "برج البرلس",
   "city": "برج البرلس",
   "governorate": "كفر الشيخ",
   "lat": 31.503,
   "lng": 30.804,
   "distanceKm": 150,
   "travelTime": "2h40"
  },
  "included": [
   "أتوبيس مكيف، القاهرة ⇄ البرلس",
   "جولتان بالقارب في البحيرة",
   "مرشد بالعربي والإنجليزي",
   "غدا بوري مشوي",
   "مياه معدنية 2 لتر",
   "دخول الميناء"
  ],
  "excluded": [
   "وقفة الفطار (حوالي 40 جنيه)",
   "مشروبات وشاي إضافي",
   "إكرامية الطاقم",
   "التوصيل من الفندق",
   "تأمين السفر"
  ],
  "itinerary": [
   {
    "time": "05:00",
    "text": "القيام من القاهرة، موقف عبد المنعم رياض",
    "icon": "directions_bus",
    "kind": "transit",
    "approximate": false
   },
   {
    "time": "07:40",
    "text": "وقفة فطار على طريق طنطا — فول وشاي ودورة مياه",
    "icon": "local_cafe",
    "kind": "meal",
    "approximate": false
   },
   {
    "time": "09:20",
    "text": "الوصول لميناء برج البرلس ومقابلة أشرف والقارب",
    "icon": "sailing",
    "kind": "transit",
    "approximate": false
   },
   {
    "time": "09:45",
    "text": "الجولة الأولى: قنوات البوص وجزر الطيور، والموتور مقفول",
    "icon": "flutter_dash",
    "kind": "activity",
    "approximate": false
   },
   {
    "time": "12:15",
    "text": "الغدا في بيت الصيادين — بوري مشوي على الفحم",
    "icon": "restaurant",
    "kind": "meal",
    "approximate": true
   },
   {
    "time": "14:00",
    "text": "لفة على جداريات برج البرلس مع عائلات الرسامين",
    "icon": "palette",
    "kind": "activity",
    "approximate": true
   },
   {
    "time": "15:30",
    "text": "الجولة الثانية: البوغاز، حيث البحيرة تقابل البحر",
    "icon": "waves",
    "kind": "activity",
    "approximate": true
   },
   {
    "time": "17:30",
    "text": "شاي على سور الميناء والغروب على البوص",
    "icon": "local_cafe",
    "kind": "activity",
    "approximate": true
   },
   {
    "time": "18:30",
    "text": "القيام للقاهرة",
    "icon": "directions_bus",
    "kind": "transit",
    "approximate": true
   },
   {
    "time": "22:00",
    "text": "الوصول لعبد المنعم رياض",
    "icon": "home",
    "kind": "transit",
    "approximate": true
   }
  ],
  "host": {
   "name": "Ashraf El Bahr",
   "avatar": {
    "url": "http://localhost:9000/bahr-assets/seed/host.png",
    "width": 240,
    "height": 240,
    "variants": []
   },
   "role": "صاحب القارب، برج البرلس",
   "tripsRun": 11,
   "verified": true,
   "phone": "+201000000101"
  },
  "tips": [
   {
    "key": "bring",
    "title": "إيه اللي تجيبه معاك",
    "body": "كاش — مفيش ماكينة سحب في البلد. قبعة وكم طويل وحذاء ميضرهوش الماء. القارب فيه ظل في نص الجولة تقريبًا."
   },
   {
    "key": "best_time",
    "title": "أفضل وقت في السنة",
    "body": "من أكتوبر لمارس للطيور، وأحلاهم نوفمبر. ابعد عن يوليو وأغسطس وقت الضهر — البحيرة مفيهاش أي حماية من الشمس."
   },
   {
    "key": "look_out",
    "title": "إيه اللي تركز عليه",
    "body": "الفلامنجو على الشطوط الشمالية بعد أول أسبوع برد. وشكل قارب أشرف — المؤخرة زي الدمعة، وما بتُبنى إلا هنا. والأبواب المرسومة في شارع البحر."
   },
   {
    "key": "know",
    "title": "حاجات لازم تعرفها",
    "body": "البحيرة عمقها لحد الرُكبة في أغلب الأماكن، بس مفيش سُتر نجاة لأطفال أقل من 4 سنين. دورات المياه في وقفة الفطار وبيت الصيادين فقط."
   }
  ],
  "reviews": {
   "average": 4.8,
   "count": 37,
   "items": [
    {
     "author": "Nada H.",
     "dateISO": "2026-09-21",
     "body": "الساعة 09:00 البحيرة ساكنة تمامًا وأشرف بيقفل الموتور علشان تسمع الطيور. خد قبعة معاك — أنا نسيت.",
     "partyLabel": "مع 3 أصحاب",
     "tone": "quaternary"
    },
    {
     "author": "Mahmoud A.",
     "dateISO": "2026-09-14",
     "body": "يوم طويل، ورجعنا القاهرة 22:40 مش 22:00. بس غدا البوري لوحده يستاهل.",
     "partyLabel": "رحلة لاثنين",
     "tone": "tertiary"
    }
   ]
  },
  "dates": [
   {
    "id": "0199c3a0-5eed-7000-8000-000000000601",
    "date": "2026-10-10",
    "dayLabel": "السبت 10 أكتوبر",
    "departTime": "05:00",
    "returnTime": "22:00",
    "seatsRemaining": 6,
    "capacity": 18,
    "soldOut": false,
    "bookable": true,
    "price": {
     "amount": 450,
     "currency": "EGP"
    }
   },
   {
    "id": "0199c3a0-5eed-7000-8000-000000000602",
    "date": "2026-10-17",
    "dayLabel": "السبت 17 أكتوبر",
    "departTime": "05:00",
    "returnTime": "22:00",
    "seatsRemaining": 2,
    "capacity": 18,
    "soldOut": false,
    "bookable": true,
    "price": {
     "amount": 450,
     "currency": "EGP"
    }
   },
   {
    "id": "0199c3a0-5eed-7000-8000-000000000603",
    "date": "2026-10-24",
    "dayLabel": "السبت 24 أكتوبر",
    "departTime": "05:00",
    "returnTime": "22:00",
    "seatsRemaining": 0,
    "capacity": 18,
    "soldOut": true,
    "bookable": false,
    "price": {
     "amount": 450,
     "currency": "EGP"
    }
   },
   {
    "id": "0199c3a0-5eed-7000-8000-000000000604",
    "date": "2026-10-31",
    "dayLabel": "السبت 31 أكتوبر",
    "departTime": "05:00",
    "returnTime": "22:00",
    "seatsRemaining": 11,
    "capacity": 18,
    "soldOut": false,
    "bookable": true,
    "price": {
     "amount": 450,
     "currency": "EGP"
    }
   }
  ],
  "policy": {
   "freeCancellationHours": 72,
   "childFreeUnder": 6,
   "maxPartySize": 6
  },
  "gallery": [
   {
    "url": "http://localhost:9000/bahr-assets/seed/joy-boats.png",
    "width": 900,
    "height": 560,
    "alt": "قوارب الصيد على بحيرة البرلس",
    "lqip": "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAwAAAAHCAYAAAA8sqwkAAAA",
    "variants": [
     {
      "url": "http://localhost:9000/bahr-assets/seed/_v/joy-boats.png/390.avif",
      "width": 390,
      "format": "avif"
     },
     {
      "url": "http://localhost:9000/bahr-assets/seed/_v/joy-boats.png/390.webp",
      "width": 390,
      "format": "webp"
     },
     {
      "url": "http://localhost:9000/bahr-assets/seed/_v/joy-boats.png/390.jpeg",
      "width": 390,
      "format": "jpeg"
     }
    ]
   },
   {
    "url": "http://localhost:9000/bahr-assets/seed/card-dawn.png",
    "width": 600,
    "height": 600,
    "alt": "البحيرة وقت الفجر",
    "lqip": "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAwAAAAHCAYAAAA8sqwkAAAA",
    "variants": [
     {
      "url": "http://localhost:9000/bahr-assets/seed/_v/card-dawn.png/390.avif",
      "width": 390,
      "format": "avif"
     },
     {
      "url": "http://localhost:9000/bahr-assets/seed/_v/card-dawn.png/390.webp",
      "width": 390,
      "format": "webp"
     },
     {
      "url": "http://localhost:9000/bahr-assets/seed/_v/card-dawn.png/390.jpeg",
      "width": 390,
      "format": "jpeg"
     }
    ]
   },
   {
    "url": "http://localhost:9000/bahr-assets/seed/joy-birds.png",
    "width": 900,
    "height": 560,
    "alt": "طيور على شطوط البحيرة",
    "lqip": "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAwAAAAHCAYAAAA8sqwkAAAA",
    "variants": [
     {
      "url": "http://localhost:9000/bahr-assets/seed/_v/joy-birds.png/390.avif",
      "width": 390,
      "format": "avif"
     },
     {
      "url": "http://localhost:9000/bahr-assets/seed/_v/joy-birds.png/390.webp",
      "width": 390,
      "format": "webp"
     },
     {
      "url": "http://localhost:9000/bahr-assets/seed/_v/joy-birds.png/390.jpeg",
      "width": 390,
      "format": "jpeg"
     }
    ]
   },
   {
    "url": "http://localhost:9000/bahr-assets/seed/joy-market.png",
    "width": 900,
    "height": 560,
    "alt": "سوق السمك في برج البرلس",
    "lqip": "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAwAAAAHCAYAAAA8sqwkAAAA",
    "variants": [
     {
      "url": "http://localhost:9000/bahr-assets/seed/_v/joy-market.png/390.avif",
      "width": 390,
      "format": "avif"
     },
     {
      "url": "http://localhost:9000/bahr-assets/seed/_v/joy-market.png/390.webp",
      "width": 390,
      "format": "webp"
     },
     {
      "url": "http://localhost:9000/bahr-assets/seed/_v/joy-market.png/390.jpeg",
      "width": 390,
      "format": "jpeg"
     }
    ]
   },
   {
    "url": "http://localhost:9000/bahr-assets/seed/joy-mural.png",
    "width": 900,
    "height": 560,
    "alt": "جدارية على بيت في برج البرلس",
    "lqip": "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAwAAAAHCAYAAAA8sqwkAAAA",
    "variants": [
     {
      "url": "http://localhost:9000/bahr-assets/seed/_v/joy-mural.png/390.avif",
      "width": 390,
      "format": "avif"
     },
     {
      "url": "http://localhost:9000/bahr-assets/seed/_v/joy-mural.png/390.webp",
      "width": 390,
      "format": "webp"
     },
     {
      "url": "http://localhost:9000/bahr-assets/seed/_v/joy-mural.png/390.jpeg",
      "width": 390,
      "format": "jpeg"
     }
    ]
   },
   {
    "url": "http://localhost:9000/bahr-assets/seed/joy-boats.png",
    "width": 900,
    "height": 560,
    "alt": "قوارب الصيد على بحيرة البرلس",
    "lqip": "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAwAAAAHCAYAAAA8sqwkAAAA",
    "variants": [
     {
      "url": "http://localhost:9000/bahr-assets/seed/_v/joy-boats.png/390.avif",
      "width": 390,
      "format": "avif"
     },
     {
      "url": "http://localhost:9000/bahr-assets/seed/_v/joy-boats.png/390.webp",
      "width": 390,
      "format": "webp"
     },
     {
      "url": "http://localhost:9000/bahr-assets/seed/_v/joy-boats.png/390.jpeg",
      "width": 390,
      "format": "jpeg"
     }
    ]
   }
  ],
  "og": {
   "image": {
    "url": "http://localhost:9000/bahr-assets/seed/joy-boats.png",
    "width": 900,
    "height": 560,
    "alt": "قوارب الصيد على بحيرة البرلس",
    "lqip": "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAwAAAAHCAYAAAA8sqwkAAAA",
    "variants": [
     {
      "url": "http://localhost:9000/bahr-assets/seed/_v/joy-boats.png/390.avif",
      "width": 390,
      "format": "avif"
     },
     {
      "url": "http://localhost:9000/bahr-assets/seed/_v/joy-boats.png/390.webp",
      "width": 390,
      "format": "webp"
     },
     {
      "url": "http://localhost:9000/bahr-assets/seed/_v/joy-boats.png/390.jpeg",
      "width": 390,
      "format": "jpeg"
     }
    ]
   },
   "title": "الفجر على بحيرة البرلس — 450 جنيه، من القاهرة ورجوع في يوم واحد",
   "description": "القيام من عبد المنعم رياض 05:00 والرجوع 22:00. الأتوبيس وجولتان بالقارب ومرشد وغدا بوري مشوي. أيام السبت، 18 مقعدًا."
  }
 }
}
        """.trimIndent()

    val arabicDepartures =
        """
{
 "success": true,
 "data": [
  {
   "id": "0199c3a0-5eed-7000-8000-000000000601",
   "date": "2026-10-10",
   "dayLabel": "السبت 10 أكتوبر",
   "departTime": "05:00",
   "returnTime": "22:00",
   "seatsRemaining": 6,
   "capacity": 18,
   "soldOut": false,
   "bookable": true,
   "price": {
    "amount": 450,
    "currency": "EGP"
   }
  },
  {
   "id": "0199c3a0-5eed-7000-8000-000000000602",
   "date": "2026-10-17",
   "dayLabel": "السبت 17 أكتوبر",
   "departTime": "05:00",
   "returnTime": "22:00",
   "seatsRemaining": 2,
   "capacity": 18,
   "soldOut": false,
   "bookable": true,
   "price": {
    "amount": 450,
    "currency": "EGP"
   }
  },
  {
   "id": "0199c3a0-5eed-7000-8000-000000000603",
   "date": "2026-10-24",
   "dayLabel": "السبت 24 أكتوبر",
   "departTime": "05:00",
   "returnTime": "22:00",
   "seatsRemaining": 0,
   "capacity": 18,
   "soldOut": true,
   "bookable": false,
   "price": {
    "amount": 450,
    "currency": "EGP"
   }
  },
  {
   "id": "0199c3a0-5eed-7000-8000-000000000604",
   "date": "2026-10-31",
   "dayLabel": "السبت 31 أكتوبر",
   "departTime": "05:00",
   "returnTime": "22:00",
   "seatsRemaining": 11,
   "capacity": 18,
   "soldOut": false,
   "bookable": true,
   "price": {
    "amount": 450,
    "currency": "EGP"
   }
  }
 ]
}
        """.trimIndent()
}
