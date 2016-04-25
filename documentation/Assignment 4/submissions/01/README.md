# Sarah Whelan
# slw96
# EECS 391: Introduction to Artificial Intelligence
# Programming Assignment 4

# Random Number Generator
Tried with varying seed values for the random number generator and the agent always ran.
However there was a significant drop in performance when the seed value was very low 4 or 5
or very high, above 10,000. I am not sure what could be causing that but it could have something
to do with not being able to explore as well if the randomness of the actions is biased.

# Features
I wrote nine features, including one that was simply a constant 1, however the agents did much
better when only the first few were used. I left in all the features as perhaps messing with
which features are used would help with the agent performance under the different random seeds.
However only the first two and the constant are used in the generation of the learnign curves/running
of the program. To use all the features just change NUM_FEATURES to a number between 0 and 9 (inclusive).

# When to reassign moves
Initially I was only reassigning targets when "an event" happened in that an agent good or bad took damage
or when an agent good or bad died. My agent did much better when the moves were reassigned every turn.

# Best weights
Best weights for 5v5 is in "bestweights.data" as per the instructions in the assignment sheet
Best weights for 10v10 is in "bestweights2.data"

# Graphs
Data and graphs can be found in "Trials.xlsx" the pdfs of the graphs created are in "5v5.pdf" and "10v10.pdf"
respectively. To average the curves the value of each trial at each ten games interval were averaged.