# karmalack

karmalack is a team karma and quote collector for your slack channels.  It is a bot.

## Overview

Teams have fun exchanges as they communicate over slack channels.  This bot lets people capture quotes and award karma.  The collected stats are puts on a portal for people to browse.

If you run a community, having karmalack available lets people award karma to each other and capture insightful or funny moments for other members to browse.  It is my hope that such a karma system would encourage community members to participate more and get into a healthy karma points competition to learn and explore new things.  For public communities the portal also becomes a way for people to advertise their skills and karma points and may be help find jobs in these communities.

## Getting Started

To get started, make a copy of the `config.example.edn` file named `config.edn` and fill it up with needed data.  Then just run:

    lein trampoline run
    
Navigate to [http://localhost:3000/](http://localhost:3000/).  To build a jar just run:

    lein uberjar
    
The jar will be written out the the `target` director.

## Configuation

You can configure the following fields for now:

 - `:slack-api-token` - This is the slack api token you can get from the [Slack API Page](https://api.slack.com/web) page.
 - `:slack-bot-token` - A slack bot token which you can get by [creating a new bot for your team](https://mazira.slack.com/services/new/bot).
 - `:datomic-uri` - A URI to the location of the Datomic database.  You can get the [Free Edition here](https://my.datomic.com/downloads/free).
 - `:base-url` - The base URL for the site used to construct server side URLs. e.g. https://somesite.com/karmalack.  The user specific URLs for the `skill` and `banner` commands are appended to this setting.


## License

Copyright © 2015 Uday Verma

Distributed under the Eclipse Public License either version 1.0 or (at your option) any later version.
