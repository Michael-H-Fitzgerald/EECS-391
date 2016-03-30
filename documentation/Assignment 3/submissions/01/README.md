# Sarah Whelan
# slw96
# 3/30/2016
# Programming Assignment 3

The only thing that may be odd about this implementation is that there is more than one way to generateChildren.

Everything in this submission was working on all the maps on Monday evening the 28th.

During class on Tuesday the 29th I learned that we were supposed to generate states that the searcher would never choose to go to.
I left the generateChildren method as it was on the 28th with the addition of an additional section that generates all of the other states
that would not be reasonable to go to (ie if a peasant is holding something and instead of going back to the town hall to deposit tries moving
to other resource locations first). 

This additional section obviously increased the runtime significantly and can be turned off by setting INCLUDE_ALL_STATES to false. I believe I set it to true for the submission.

I am sorry for the inconvenience of separating it this way but I wasn't sure if I was supposed to optimize for staying as true to the A* search
as possible or trying to optimize for run time and I think this is a reasonable compromise.

The non-build peasant maps generate a plan in less than a second.
The small build peasant map runs in under 8 minutes my average was 5 min 30 sec.
The large build peasant map (I had to increase the memory given to the jvm) but once I did it took about half an hour to run.

Additionally for my implementation I made two changes to the provided xml files that are not included in this zip.

midasSmall_BuildPeasant.xml:

Change both arguments to 1000 (were both 700)
Before this change my peasants would collect 700 of each and stop but the simulation wouldn't stop until the turn limit was reached.
This is because the rest of the xml file specified the 1000 required to end.

midasLarge_BuildPeasant.xml:

Changed the wood required argument from 3000 to 2000
There is only 2000 wood on the entire map so asking for more isn't going to work.
Before this change my implementation printed No plan found.

Thank you. Please let me know if something doesn't make sense.