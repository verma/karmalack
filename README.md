# karmalack

karmalack is a team karma and quote collector for your slack channels.  It is a bot.

## Overview

Teams have fun exchanges as they communicate over slack channels.  This bot lets people capture quotes and award karma.  The collected stats are puts on a portal for people to browse.

If you run a community, having karmalack available lets people award karma to each other and capture insightful or funny moments for other members to browse.  It is my hope that such a karma system would encourage community members to participate more and get into a healthy karma points competition to learn and explore new things.  For public communities the portal also becomes a way for people to advertise their skills and karma points and may be help find jobs in these communities.

## Getting Started

To get started, make a copy of the `config.example.edn` file named `config.edn` and fill it up with needed data.  Then just run:

    lein figwheel
    
Navigate to [http://localhost:3449/](http://localhost:3449/).

You can configure the following fields for now:

 - `:slack-api-token` - This is the slack api token you can get from the [Slack API Page](https://api.slack.com/web) page.
 - `:slack-bot-token` - A slack bot token which you can get by [creating a new bot for your team](https://mazira.slack.com/services/new/bot).


## License

Copyright © 2015 Uday Verma

Distributed under the Eclipse Public License either version 1.0 or (at your option) any later version.
