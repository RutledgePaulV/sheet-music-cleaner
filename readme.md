### What

A small program to prepare sheet music of various dimensions for printing on standard `8.5" x 11"` paper.

### Why

There is plenty of sheet music available in the public domain from websites like IMSLP. Unfortunately, there are no standard dimensions and the scan quality varies considerably and often includes excessive padding. These problems lead to music that skews off the page when printed on standard paper or else it scales down becomes harder to read.

Previously I've used Adobe software to manually clean up and resize such music, but it's a tedious process. I'd like to make it easy to do without an Adobe subscription.


### Usage

```shell
clj -X:run < input.pdf > output.pdf
```
