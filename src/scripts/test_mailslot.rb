#!/usr/bin/ruby

require 'net/smtp'

smtp = Net::SMTP.new('localhost', 10025)
smtp.esmtp = false
#smtp.set_debug_output $stderr
now = Time.now.to_i
puts "sending 1000 emails"
1000.times do
  smtp.start("localhost") do |smtp|
    smtp.send_message "From: stevej@pobox.com\n\nhello, work email address!", 'stevej@pobox.com', 'stevej@twitter.com'
  end
end
completed = Time.now.to_i - now
puts "Average milliseconds to send email: #{completed}"
