# Sarah Whelan
# slw96
# 3/30/2016
# Programming Assignment 3

I made two changes to the provided xml files that are not included in this zip.

midasSmall_BuildPeasant.xml:

Change both arguments to 1000 (were both 700)
Before this change my peasants would collect 700 of each and stop but the simulation wouldn't stop until the turn limit was reached.
This is because the rest of the xml file specified the 1000 required to end.

midasLarge_BuildPeasant.xml:

Changed the wood required argument from 3000 to 2000
Changed the gold required argument from 2000 to 3000
There is only 2000 wood on the entire map so asking for more isn't going to work.
Before this change my implementation printed No plan found.

Thank you. Please let me know if something doesn't make sense.