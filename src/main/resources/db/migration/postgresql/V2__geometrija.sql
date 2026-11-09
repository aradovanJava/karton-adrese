-- Linijska i površinska geometrija objekata (biciklističke staze, parkovi, gradski vrtovi):
-- udaljenost se mjeri do ruba, a ne do središta. Zapis je kompaktan JSON ({"t":"L","d":[[[lat,lon],…]]}).
ALTER TABLE poi ADD COLUMN geometrija TEXT;
