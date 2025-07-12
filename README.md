This project addresses an issue we had moving from Vireo 3 to Vireo 4. Our verson 3 instance only tracked selected 
custom action values on submissions in the database,
apparently leaving it to the display logic to create
checkbox controls for unselected custom action values in
the UI. In Vireo 4 however new submissions are created with values for all
custom actions recorded in the database, and the UI
expects all of them to be present in the database in order to display checkbox controls 
for them. As a result, submissions which were created in a 
version 3 instance and migrated over to version 4 would
be missing the checkbox controls for custom actions. This 
meant that unselected actions could not be selected.

We chose to deal with this issue by simply poulating the database with the
missing custom action values for every submission. The program looks for missing
custom action values for each submission, and inserts a value of false for these. This results in each
submission having a value for each custom action, which allows the UI to present a full set of 
controls for each submission.
